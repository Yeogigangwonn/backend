package com.app.yeogigangwon.fetch;

import com.app.yeogigangwon.dto.WeatherInfo;
import com.app.yeogigangwon.util.GridConverter;
import com.app.yeogigangwon.util.GridConverter.GridCoordinate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 기상청 단기 예보 API 호출 클래스
 * 기상청 공공데이터 포털의 단기예보 API를 호출하여 날씨 정보를 가져옴
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastFetcher {

    private final RestTemplate restTemplate;
    
    // 기상청 공공데이터 포털 API 키
    @Value("${weather.api.key}")
    private String apiKey;
    
    // 기상청 단기예보 API 기본 URL
    private static final String BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";

    /**
     * 기상청 단기예보 API 호출
     * 
     * @param nx 격자 X 좌표
     * @param ny 격자 Y 좌표
     * @return 날씨 정보 (API 실패 시 예외 발생)
     * @throws RuntimeException API 호출 실패 시
     */
    public WeatherInfo fetchWeatherForecast(int nx, int ny) {
        log.info("기상청 단기예보 API 호출 시작 - 격자: ({}, {})", nx, ny);
        
        try {
            // 현재 시간 기준으로 가장 가까운 예보 시각 계산
            LocalDateTime now = LocalDateTime.now();
        String baseDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = getNearestBaseTime(now.getHour());

            log.debug("예보 기준 시각: {} {}", baseDate, baseTime);
            
            // API URL 구성
            String url = buildApiUrl(nx, ny, baseDate, baseTime);
            log.debug("기상청 API URL (API 키 제외): {}", url.replace(apiKey, "***"));
            
            // API 호출 (URI 객체 사용으로 이중 인코딩 방지)
            URI uri = URI.create(url);
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            
            // HTTP 상태 코드 확인
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("기상청 API 호출 성공 - 상태코드: {}", response.getStatusCode());
                String responseBody = response.getBody();
                
                // XML 응답 검증 (기상청 에러 응답 감지)
                if (responseBody != null && responseBody.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")) {
                    log.error("기상청 API 키가 서비스에 등록되지 않았습니다: {}", responseBody);
                    throw new IllegalStateException("기상청 API 키가 서비스에 등록되지 않았습니다. 공공데이터포털에서 API 키 등록을 확인해주세요.");
                }
                
                // HTML/XML 응답 감지 (에러 페이지 등)
                if (responseBody != null && (responseBody.trim().startsWith("<") || responseBody.contains("<html") || responseBody.contains("<!DOCTYPE"))) {
                    log.error("기상청이 HTML/XML 오류 응답을 반환했습니다: {}", responseBody.substring(0, Math.min(200, responseBody.length())));
                    throw new IllegalStateException("기상청이 오류 응답을 반환했습니다. API 키나 서비스 등록 상태를 확인해주세요.");
                }
                
                // JSON 응답 검증
                if (responseBody == null || !responseBody.trim().startsWith("{")) {
                    log.error("기상청 응답이 JSON이 아닙니다: {}", responseBody != null ? responseBody.substring(0, Math.min(200, responseBody.length())) : "null");
                    throw new IllegalStateException("기상청이 JSON이 아닌 응답을 반환했습니다.");
                }
                
                // 응답 파싱
                return parseWeatherResponse(responseBody);
            } else {
                log.error("기상청 API 호출 실패 - 상태코드: {}, 응답: {}", 
                         response.getStatusCode(), response.getBody());
                throw new RuntimeException("기상청 API 호출 실패: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("기상청 단기예보 조회 실패", e);
            throw new RuntimeException("기상청 단기예보 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 기상청 API URL 구성
     */
    private String buildApiUrl(int nx, int ny, String baseDate, String baseTime) {
        return BASE_URL
                + "?serviceKey=" + apiKey
                + "&pageNo=1"
                + "&numOfRows=200"
                + "&dataType=JSON"
                + "&base_date=" + baseDate
                + "&base_time=" + baseTime
                + "&nx=" + nx
                + "&ny=" + ny;
    }
    
    /**
     * 기상청 API 응답을 파싱하여 WeatherInfo로 변환
     */
    private WeatherInfo parseWeatherResponse(String responseBody) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> root = objectMapper.readValue(responseBody, Map.class);

        // 응답 구조 파싱
        Map<String, Object> body = (Map<String, Object>) ((Map<String, Object>) root.get("response")).get("body");
        if (body == null) {
            throw new IllegalStateException("response.body가 없습니다");
        }

        Map<String, Object> items = (Map<String, Object>) body.get("items");
        if (items == null) {
            throw new IllegalStateException("response.body.items가 없습니다");
        }

            Object itemNode = items.get("item");
        if (itemNode == null) {
            throw new IllegalStateException("response.body.items.item이 없습니다");
        }

        // item이 단일 객체인지 리스트인지 확인
            List<Map<String, Object>> itemList;
            if (itemNode instanceof List) {
                itemList = (List<Map<String, Object>>) itemNode;
            } else {
                itemList = new ArrayList<>();
                itemList.add((Map<String, Object>) itemNode);
            }

        // 현재 시간에 가장 가까운 미래 예보 찾기
        LocalDateTime now = LocalDateTime.now();

        // 시간대별로 데이터 그룹화
        Map<String, Map<String, Object>> forecastByTime = new HashMap<>();
            for (Map<String, Object> item : itemList) {
            String fcstDate = String.valueOf(item.get("fcstDate"));
            String fcstTime = String.valueOf(item.get("fcstTime"));
            String timeKey = fcstDate + fcstTime;
            
            if (!forecastByTime.containsKey(timeKey)) {
                forecastByTime.put(timeKey, new HashMap<>());
            }
            forecastByTime.get(timeKey).put(String.valueOf(item.get("category")), item.get("fcstValue"));
        }
        
        // 현재 시간에 가장 가까운 예보 찾기
        final Map<String, Object>[] closestForecast = new Map[1];
        int currentHour = now.getHour();
        
        // 현재 시간에 가장 가까운 예보 찾기
        for (Map.Entry<String, Map<String, Object>> entry : forecastByTime.entrySet()) {
            String timeKey = entry.getKey();
            String fcstDate = timeKey.substring(0, 8);
            String fcstTime = timeKey.substring(8, 12);
            
            try {
                // 현재 날짜와 같은 예보만 고려
                String currentDateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                if (fcstDate.equals(currentDateStr)) {
                    int forecastHour = Integer.parseInt(fcstTime.substring(0, 2));
                    int timeDiff = Math.abs(currentHour - forecastHour);
                    
                    // 현재 시간에 가장 가까운 예보 선택
                    if (closestForecast[0] == null) {
                        closestForecast[0] = entry.getValue();
                    } else {
                        // 더 가까운 예보가 있는지 확인
                        int currentClosestHour = Integer.parseInt(
                            forecastByTime.entrySet().stream()
                                .filter(e -> e.getValue() == closestForecast[0])
                                .findFirst()
                                .get()
                                .getKey()
                                .substring(8, 10)
                        );
                        
                        if (timeDiff < Math.abs(currentHour - currentClosestHour)) {
                            closestForecast[0] = entry.getValue();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("예보 시간 처리 실패: {}", timeKey, e);
            }
        }
        
        // 가장 가까운 예보가 없으면 첫 번째 예보 사용
        if (closestForecast[0] == null && !forecastByTime.isEmpty()) {
            closestForecast[0] = forecastByTime.values().iterator().next();
            log.warn("예보를 찾지 못해 첫 번째 예보를 사용합니다");
        }
        
        if (closestForecast[0] == null) {
            throw new IllegalStateException("유효한 예보 데이터가 없습니다");
        }
        
        // 선택된 예보에서 데이터 추출
        Integer tmp = null, pop = null, sky = null, wsd = null;
        
        if (closestForecast[0].containsKey("TMP")) {
            tmp = Integer.parseInt(String.valueOf(closestForecast[0].get("TMP")));
            log.info("선택된 기온: {}도", tmp);
        }
        if (closestForecast[0].containsKey("POP")) {
            pop = Integer.parseInt(String.valueOf(closestForecast[0].get("POP")));
        }
        if (closestForecast[0].containsKey("SKY")) {
            sky = Integer.parseInt(String.valueOf(closestForecast[0].get("SKY")));
        }
        if (closestForecast[0].containsKey("WSD")) {
            wsd = (int) Float.parseFloat(String.valueOf(closestForecast[0].get("WSD")));
        }

        // 필수 데이터 검증
            if (tmp == null || pop == null || sky == null || wsd == null) {
            throw new IllegalStateException("필수 날씨 데이터(TMP/POP/SKY/WSD)가 누락되었습니다");
            }

            return new WeatherInfo(tmp, pop, sky, wsd);
    }
    

    /**
     * 현재 시간에 가장 가까운 기상청 예보 기준 시각 반환
     * 기상청은 3시간마다 예보를 발표 (02, 05, 08, 11, 14, 17, 20, 23시)
     * 
     * @param hour 현재 시간 (0-23)
     * @return 예보 기준 시각 (HHMM 형식)
     */
    private String getNearestBaseTime(int hour) {
        if (hour < 2) return "2300";
        else if (hour < 5) return "0200";
        else if (hour < 8) return "0500";
        else if (hour < 11) return "0800";
        else if (hour < 14) return "1100";
        else if (hour < 17) return "1400";
        else if (hour < 20) return "1700";
        else if (hour < 23) return "2000";
        else return "2300";
    }
}

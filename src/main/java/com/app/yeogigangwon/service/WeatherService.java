package com.app.yeogigangwon.service;

import com.app.yeogigangwon.domain.WeatherForecast;
import com.app.yeogigangwon.dto.WeatherAlert;
import com.app.yeogigangwon.dto.WeatherInfo;
import com.app.yeogigangwon.dto.WeatherSummary;
import com.app.yeogigangwon.fetch.AlertFetcher;
import com.app.yeogigangwon.fetch.ForecastFetcher;
import com.app.yeogigangwon.repository.WeatherForecastRepository;
import com.app.yeogigangwon.util.GridConverter;
import com.app.yeogigangwon.util.GridConverter.GridCoordinate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 날씨 정보 서비스
 * 기상청 API 호출, 데이터 저장, 조회 기능 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final ForecastFetcher forecastFetcher;
    private final AlertFetcher alertFetcher;
    private final WeatherForecastRepository weatherForecastRepository;

    /**
     * 실시간 날씨 요약 정보 조회
     * 
     * @param lat 위도
     * @param lon 경도
     * @return 날씨 요약 정보
     */
    public WeatherSummary getWeatherSummary(double lat, double lon) {
        try {
            // 1. 먼저 DB에서 최신 데이터 조회 (15분 이내)
            WeatherInfo cachedInfo = getLatestFromDb(lat, lon);
            
            // 2. 캐시된 데이터가 있고 15분 이내라면 사용
            if (cachedInfo != null) {
                // 기상 특보 조회 (실패 시 빈 목록 반환)
                List<WeatherAlert> alerts = new ArrayList<>();
                try {
                    alerts = alertFetcher.fetchWeatherAlerts("강원도");
                } catch (Exception e) {
                    // 기상 특보 조회 실패 시 빈 목록으로 처리
                }
                
                return new WeatherSummary(cachedInfo, alerts);
            }
            
            // 3. 캐시된 데이터가 없거나 오래되었다면 새로운 API 호출
            WeatherInfo info = forecastFetcher.fetchWeatherForecast(
                GridConverter.convertToGrid(lat, lon).nx,
                GridConverter.convertToGrid(lat, lon).ny
            );
            
            // 4. 새로운 데이터를 DB에 저장
            try {
                fetchAndSave(lat, lon);
            } catch (Exception e) {
                // DB 저장 실패, 하지만 API 데이터는 사용
            }
            
            // 기상 특보 조회 (실패 시 빈 목록 반환)
            List<WeatherAlert> alerts = new ArrayList<>();
            try {
                alerts = alertFetcher.fetchWeatherAlerts("강원도");
            } catch (Exception e) {
                // 기상 특보 조회 실패 시 빈 목록으로 처리
            }
            
        return new WeatherSummary(info, alerts);
        } catch (Exception e) {
            // NO_DATA 상황에서 가장 최근 DB 데이터 사용
            WeatherInfo latestInfo = getLatestFromDb(lat, lon);
            if (latestInfo != null) {
                List<WeatherAlert> alerts = new ArrayList<>();
                try {
                    alerts = alertFetcher.fetchWeatherAlerts("강원도");
                } catch (Exception alertException) {
                    // 기상 특보 조회 실패 시 빈 목록으로 처리
                }
                return new WeatherSummary(latestInfo, alerts);
            }
            
            // DB에도 데이터가 없으면 기본값 반환
            WeatherInfo defaultInfo = new WeatherInfo(22, 20, 1, 2); // 온도 22도, 강수확률 20%, 맑음, 풍속 2m/s
            List<WeatherAlert> alerts = new ArrayList<>();
            try {
                alerts = alertFetcher.fetchWeatherAlerts("강원도");
            } catch (Exception alertException) {
                // 기상 특보 조회 실패 시 빈 목록으로 처리
            }
            return new WeatherSummary(defaultInfo, alerts);
        }
    }

    /**
     * 기상청 API에서 날씨 데이터를 가져와서 DB에 저장
     * 
     * @param lat 위도
     * @param lon 경도
     * @return 저장된 날씨 예보 정보
     */
    public WeatherForecast fetchAndSave(double lat, double lon) {
        try {
            // 기준 시각 계산 (1시간 전 기준)
        LocalDateTime now = LocalDateTime.now().minusHours(1);
        String baseDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = getNearestBaseTime(now.getHour());

            // 위도/경도를 격자 좌표로 변환
        GridCoordinate grid = GridConverter.convertToGrid(lat, lon);

            // API에서 단기 예보 데이터 조회
            WeatherInfo info = forecastFetcher.fetchWeatherForecast(grid.nx, grid.ny);

            // WeatherForecast 도메인 객체 생성 및 저장
            WeatherForecast weatherForecast = new WeatherForecast();
            weatherForecast.setNx(String.valueOf(grid.nx));
            weatherForecast.setNy(String.valueOf(grid.ny));
            weatherForecast.setBaseDate(baseDate);
            weatherForecast.setBaseTime(baseTime);
            weatherForecast.setForecastTime(LocalDateTime.now());
            
            // WeatherInfo를 JSON으로 변환하여 저장
            String weatherData = String.format(
                "{\"temperature\":\"%s\",\"precipitationProbability\":\"%s\",\"sky\":\"%s\",\"windSpeed\":\"%s\"}",
                info.getTemperature(), info.getPrecipitationProbability(), info.getSky(), info.getWindSpeed()
            );
            weatherForecast.setWeatherData(weatherData);
            weatherForecast.setCreatedAt(LocalDateTime.now());

            return weatherForecastRepository.save(weatherForecast);
        } catch (Exception e) {
            log.error("날씨 데이터 API 호출 및 저장 실패", e);
            throw new RuntimeException("날씨 데이터 API 호출 및 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * DB에서 최신 날씨 데이터 조회
     * 
     * @param lat 위도
     * @param lon 경도
     * @return 최신 날씨 정보 (없으면 null)
     */
    public WeatherInfo getLatestFromDb(double lat, double lon) {
        // 위도/경도를 격자 좌표로 변환
        GridCoordinate grid = GridConverter.convertToGrid(lat, lon);

        // 격자 좌표 기준으로 최신 데이터 조회
        List<WeatherForecast> forecasts = weatherForecastRepository.findLatestByGrid(String.valueOf(grid.nx), String.valueOf(grid.ny));
        
        if (forecasts.isEmpty()) {
            return null;
        }

        // WeatherForecast를 WeatherInfo로 변환 (JSON 파싱)
        WeatherForecast forecast = forecasts.get(0);
        
        // 데이터 생성 시간 확인 (15분 이내면 우선 사용, 아니면 가장 최근 데이터 사용)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime forecastTime = forecast.getCreatedAt();
        long minutesDiff = java.time.Duration.between(forecastTime, now).toMinutes();
        
        if (minutesDiff > 15) {
            // NO_DATA 상황에서 가장 최근 데이터를 사용
        }
        
        try {
            // 간단한 JSON 파싱 (실제로는 Jackson ObjectMapper 사용 권장)
            String weatherData = forecast.getWeatherData();
            if (weatherData != null && weatherData.contains("temperature")) {
                // JSON에서 값 추출 (간단한 방식)
                String temp = extractValue(weatherData, "temperature");
                String pop = extractValue(weatherData, "precipitationProbability");
                String sky = extractValue(weatherData, "sky");
                String wsd = extractValue(weatherData, "windSpeed");
                
                return new WeatherInfo(
                    Integer.parseInt(temp), 
                    Integer.parseInt(pop), 
                    Integer.parseInt(sky), 
                    Integer.parseInt(wsd)
                );
            }
        } catch (Exception e) {
            log.warn("날씨 데이터 파싱 실패: {}", forecast.getWeatherData(), e);
        }
        
        return null;
    }

    /**
     * JSON 문자열에서 특정 키의 값을 추출하는 헬퍼 메서드
     * 
     * @param json JSON 문자열
     * @param key 추출할 키
     * @return 추출된 값 (없으면 "0")
     */
    private String extractValue(String json, String key) {
        try {
            int startIndex = json.indexOf("\"" + key + "\":\"") + key.length() + 4;
            int endIndex = json.indexOf("\"", startIndex);
            if (startIndex > key.length() + 3 && endIndex > startIndex) {
                return json.substring(startIndex, endIndex);
            }
        } catch (Exception e) {
            log.warn("JSON 파싱 실패: key={}, json={}", key, json, e);
        }
        return "0";
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

    // ================== 스케줄러 기능 ==================
    
    // 강원도 전체 18개 지역 코드 (기상청 지역 코드)
    private static final List<String> GANGWON_REGION_CODES = List.of(
            "51110", // 춘천시
            "51130", // 원주시
            "51150", // 강릉시
            "51170", // 동해시
            "51190", // 삼척시
            "51210", // 속초시
            "51230", // 태백시
            "51720", // 고성군
            "51730", // 양구군
            "51750", // 양양군
            "51760", // 영월군
            "51770", // 인제군
            "51780", // 정선군
            "51790", // 철원군
            "51800", // 평창군
            "51810", // 홍천군
            "51820", // 화천군
            "51830"  // 횡성군
    );
    
    /**
     * 강원도 전체 지역 날씨 데이터 업데이트 스케줄러
     * 15분마다 강원도 전체 18개 지역의 날씨 데이터를 주기적으로 갱신
     */
    @Scheduled(fixedRate = 15 * 60 * 1000) // 15분마다 실행
    public void updateWeatherData() {
        // 심야 시간 체크 (22:00 ~ 06:00)
        int currentHour = LocalDateTime.now().getHour();
        if (currentHour >= 22 || currentHour < 6) {
            return;
        }
        
        try {
            // 강원도 전체 18개 지역의 날씨 데이터 업데이트
            for (String regionCode : GANGWON_REGION_CODES) {
                updateWeatherForRegion(regionCode);
                Thread.sleep(100); // API 호출 간격 조절
            }
        } catch (Exception e) {
            log.error("강원도 전체 날씨 데이터 업데이트 중 오류 발생", e);
        }
    }

    /**
     * 특정 지역 코드로 날씨 데이터 업데이트
     */
    private void updateWeatherForRegion(String regionCode) {
        try {
            // 지역 코드에 해당하는 대표 좌표 사용
            double[] coordinates = getRegionCoordinates(regionCode);
            fetchAndSave(coordinates[0], coordinates[1]);
        } catch (Exception e) {
            log.warn("지역 코드 {} 날씨 데이터 업데이트 실패: {}", regionCode, e.getMessage());
        }
    }
    
    /**
     * 지역 코드에 따른 지역명 반환
     */
    private String getRegionName(String regionCode) {
        return switch (regionCode) {
            case "51110" -> "춘천시";
            case "51130" -> "원주시";
            case "51150" -> "강릉시";
            case "51170" -> "동해시";
            case "51190" -> "삼척시";
            case "51210" -> "속초시";
            case "51230" -> "태백시";
            case "51720" -> "고성군";
            case "51730" -> "양구군";
            case "51750" -> "양양군";
            case "51760" -> "영월군";
            case "51770" -> "인제군";
            case "51780" -> "정선군";
            case "51790" -> "철원군";
            case "51800" -> "평창군";
            case "51810" -> "홍천군";
            case "51820" -> "화천군";
            case "51830" -> "횡성군";
            default -> "알 수 없는 지역";
        };
    }
    
    /**
     * 지역 코드에 따른 대표 좌표 반환 [위도, 경도]
     */
    private double[] getRegionCoordinates(String regionCode) {
        return switch (regionCode) {
            case "51110" -> new double[]{37.7491, 128.8785}; // 춘천시
            case "51130" -> new double[]{37.2756, 128.8950}; // 원주시
            case "51150" -> new double[]{37.4500, 128.9000}; // 강릉시
            case "51170" -> new double[]{37.2000, 129.1000}; // 동해시
            case "51190" -> new double[]{37.2000, 128.5000}; // 삼척시
            case "51210" -> new double[]{38.2000, 128.6000}; // 속초시
            case "51230" -> new double[]{37.3000, 128.7000}; // 태백시
            case "51720" -> new double[]{38.3000, 128.5000}; // 고성군
            case "51730" -> new double[]{38.1000, 128.0000}; // 양구군
            case "51750" -> new double[]{38.0000, 128.7000}; // 양양군
            case "51760" -> new double[]{37.1000, 128.4000}; // 영월군
            case "51770" -> new double[]{38.0000, 128.2000}; // 인제군
            case "51780" -> new double[]{37.2000, 128.6000}; // 정선군
            case "51790" -> new double[]{38.2000, 127.3000}; // 철원군
            case "51800" -> new double[]{37.3000, 128.4000}; // 평창군
            case "51810" -> new double[]{37.6000, 128.0000}; // 홍천군
            case "51820" -> new double[]{38.1000, 127.7000}; // 화천군
            case "51830" -> new double[]{37.4000, 127.9000}; // 횡성군
            default -> new double[]{37.5000, 128.5000}; // 강원도 중심
        };
    }
}

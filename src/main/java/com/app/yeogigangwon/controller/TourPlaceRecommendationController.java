package com.app.yeogigangwon.controller;

import com.app.yeogigangwon.dto.RecommendationRequest;
import com.app.yeogigangwon.dto.TourPlaceRecommendation;
import com.app.yeogigangwon.service.TourPlaceRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 관광지 추천 API 컨트롤러
 * 혼잡도, 거리, 테마, 날씨를 종합적으로 고려한 관광지 추천
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TourPlaceRecommendationController {

    private final TourPlaceRecommendationService recommendationService;

    /**
     * 종합적인 관광지 추천 (POST 방식)
     * 
     * @param request 추천 요청 정보
     * @return 추천된 관광지 목록 (점수순 정렬)
     */
    @PostMapping("/places/recommend")
    public ResponseEntity<List<TourPlaceRecommendation>> getRecommendations(
            @RequestBody RecommendationRequest request) {
        
        log.info("관광지 추천 요청 - 위치: ({}, {}), 선호테마: {}", 
                request.getLatitude(), request.getLongitude(), request.getPreferredThemes());

        // 기본값 설정
        if (request.getMaxDistance() <= 0) request.setMaxDistance(50);
        if (request.getLimit() <= 0) request.setLimit(10);

        List<TourPlaceRecommendation> recommendations = recommendationService.getRecommendations(request);
        
        return ResponseEntity.ok(recommendations);
    }

    /**
     * 간편한 관광지 추천 (GET 방식)
     * 
     * @param lat 위도
     * @param lon 경도
     * @param themes 선호 테마 (쉼표로 구분, 예: "해변,산,실내")
     * @param maxDistance 최대 거리 (km, 기본값: 50)
     * @param limit 추천 개수 (기본값: 10)
     * @param avoidCrowded 혼잡한 곳 피하기 (기본값: true)
     * @param considerWeather 날씨 고려하기 (기본값: true)
     * @param transportationMode 이동 수단 (CAR/WALKING, 기본값: CAR)
     * @param maxTravelTime 최대 이동 시간 (분, 기본값: 60)
     * @param considerTravelTime 이동 시간 고려하기 (기본값: true)
     * @return 추천된 관광지 목록
     */
    @GetMapping("/places/recommend")
    public ResponseEntity<List<TourPlaceRecommendation>> getRecommendationsSimple(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false) String themes,
            @RequestParam(defaultValue = "50") int maxDistance,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "true") boolean avoidCrowded,
            @RequestParam(defaultValue = "true") boolean considerWeather,
            @RequestParam(defaultValue = "CAR") String transportationMode,
            @RequestParam(defaultValue = "60") int maxTravelTime,
            @RequestParam(defaultValue = "true") boolean considerTravelTime) {

        log.info("간편 관광지 추천 요청 - 위치: ({}, {}), 테마: {}, 이동수단: {}", lat, lon, themes, transportationMode);

        // 테마 파싱
        List<String> preferredThemes = null;
        if (themes != null && !themes.trim().isEmpty()) {
            preferredThemes = Arrays.asList(themes.split(","));
            preferredThemes = preferredThemes.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        // 이동 수단 파싱
        RecommendationRequest.TransportationMode mode;
        try {
            mode = RecommendationRequest.TransportationMode.valueOf(transportationMode.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 이동 수단: {}, 기본값 CAR 사용", transportationMode);
            mode = RecommendationRequest.TransportationMode.CAR;
        }

        RecommendationRequest request = new RecommendationRequest(
                lat, lon, preferredThemes, maxDistance, limit, avoidCrowded, considerWeather,
                mode, maxTravelTime, considerTravelTime
        );

        List<TourPlaceRecommendation> recommendations = recommendationService.getRecommendations(request);
        
        return ResponseEntity.ok(recommendations);
    }

}

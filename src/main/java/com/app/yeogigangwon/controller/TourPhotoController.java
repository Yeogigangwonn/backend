package com.app.yeogigangwon.controller;

import com.app.yeogigangwon.dto.TourPhoto;
import com.app.yeogigangwon.service.TourPhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관광사진 관련 API 컨트롤러
 * 한국관광공사 관광사진갤러리 API를 활용한 사진 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class TourPhotoController {

    private final TourPhotoService tourPhotoService;

    /**
     * 관광지 사진 목록 조회
     * 
     * @param page 페이지 번호 (기본값: 1)
     * @param size 한 페이지 결과 수 (기본값: 10)
     * @return 관광지 사진 목록
     */
    @GetMapping
    public ResponseEntity<List<TourPhoto>> getTourPlacePhotos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        log.info("=== TourPhotoController.getTourPlacePhotos 호출됨 ===");
        log.info("관광지 사진 목록 조회 요청 - 페이지: {}, 크기: {}", page, size);
        
        List<TourPhoto> photos = tourPhotoService.getTourPlacePhotos(size, page);
        
        log.info("TourPhotoService에서 받은 사진 개수: {}", photos.size());
        return ResponseEntity.ok(photos);
    }
}

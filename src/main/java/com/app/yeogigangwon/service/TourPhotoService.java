package com.app.yeogigangwon.service;

import com.app.yeogigangwon.dto.TourPhoto;
import com.app.yeogigangwon.fetch.PhotoGalleryFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 관광사진 관련 비즈니스 로직을 처리하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourPhotoService {

    private final PhotoGalleryFetcher photoGalleryFetcher;

    /**
     * 관광지 사진 조회 (필터링 없음)
     */
    public List<TourPhoto> getTourPlacePhotos(int numOfRows, int pageNo) {
        log.info("관광지 사진 조회 요청 - 페이지: {}, 개수: {}", pageNo, numOfRows);
        
        List<TourPhoto> photos = photoGalleryFetcher.getTourPhotos(numOfRows, pageNo);
        
        List<TourPhoto> validPhotos = photos.stream()
                .filter(photo -> photo.getGalWebImageUrl() != null && !photo.getGalWebImageUrl().trim().isEmpty())
                .collect(Collectors.toList());
        
        log.info("유효한 관광지 사진 {}개 반환", validPhotos.size());
        return validPhotos;
    }
}

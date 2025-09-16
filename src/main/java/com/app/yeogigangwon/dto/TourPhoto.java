package com.app.yeogigangwon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관광사진 정보 DTO
 * 한국관광공사 관광사진갤러리 API 응답 데이터
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TourPhoto {
    
    private String galContentId;      // 갤러리 콘텐츠 ID
    private String galContentTypeId;  // 갤러리 콘텐츠 타입 ID
    private String galTitle;          // 사진 제목
    private String galWebImageUrl;    // 웹 이미지 URL
    private String galCreatedtime;    // 촬영일
    private String galModifiedtime;   // 수정일
    private String galPhotographyLocation; // 촬영 위치
    private String galPhotographer;   // 촬영자
    private String galSearchKeyword;  // 검색 키워드
    private String galPhotographyMonth; // 촬영 월
    private String galPhotographyLocationName; // 촬영 위치명
    private String galPhotographyLocationAddress; // 촬영 위치 주소
    private String galPhotographyLocationLatitude; // 촬영 위치 위도
    private String galPhotographyLocationLongitude; // 촬영 위치 경도
}

package com.app.yeogigangwon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 관광사진갤러리 목록 조회 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhotoGalleryResponse {
    
    private Response response;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Header header;
        private Body body;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        private Items items;
        private int numOfRows;
        private int pageNo;
        private int totalCount;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Items {
        private List<TourPhoto> item;
    }
}

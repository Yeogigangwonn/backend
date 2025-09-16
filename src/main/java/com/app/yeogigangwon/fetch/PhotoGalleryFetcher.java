package com.app.yeogigangwon.fetch;

import com.app.yeogigangwon.dto.TourPhoto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 한국관광공사 관광사진갤러리 API 호출 클래스
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoGalleryFetcher {

    private final RestTemplate restTemplate;

    @Value("${tour.photo.api.key:}")
    private String serviceKey;

    private static final String BASE_URL = "http://apis.data.go.kr/B551011/PhotoGalleryService1/galleryList1";

    /**
     * 관광사진갤러리 목록 조회
     * 
     * @param numOfRows 한 페이지 결과 수 (기본값: 10)
     * @param pageNo 페이지 번호 (기본값: 1)
     * @param arrange 정렬 구분 (A=제목순, B=조회순, C=수정일순, D=촬영일순)
     * @param contentTypeId 콘텐츠 타입 ID (12=관광지, 14=문화시설, 15=축제공연행사, 25=여행코스, 28=레포츠, 32=숙박, 38=쇼핑, 39=음식점)
     * @return 관광사진 목록
     */
    public List<TourPhoto> getPhotoGalleryList(int numOfRows, int pageNo, String arrange, String contentTypeId) {
        try {
            // API 키 확인
            if (serviceKey == null || serviceKey.trim().isEmpty()) {
                log.error("API 키가 설정되지 않았습니다. serviceKey: '{}'", serviceKey);
                return List.of();
            }
            
            
            String url = UriComponentsBuilder.fromUriString(BASE_URL)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("numOfRows", numOfRows)
                    .queryParam("pageNo", pageNo)
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "YeogiGangwon")
                    .queryParam("arrange", arrange != null ? arrange : "D")
                    .build()
                    .toUriString();
            
            String xmlResponse = restTemplate.getForObject(url, String.class);
            
            if (xmlResponse == null || xmlResponse.trim().isEmpty()) {
                log.warn("API 응답이 비어있습니다");
                return List.of();
            }
            
            List<TourPhoto> photos = parseXmlResponse(xmlResponse);
            
            return photos;

        } catch (Exception e) {
            log.error("관광사진 API 호출 실패", e);
            return List.of();
        }
    }

    /**
     * 관광지 사진 조회 (프론트엔드용 - 필터링 없음)
     */
    public List<TourPhoto> getTourPhotos(int numOfRows, int pageNo) {
        return getPhotoGalleryList(numOfRows, pageNo, "D", null);
    }

    /**
     * XML 응답을 파싱하여 TourPhoto 리스트로 변환
     */
    private List<TourPhoto> parseXmlResponse(String xmlResponse) {
        List<TourPhoto> photos = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            // UTF-8 인코딩으로 명시적으로 처리
            byte[] xmlBytes = xmlResponse.getBytes("UTF-8");
            Document document = builder.parse(new ByteArrayInputStream(xmlBytes));
            
            NodeList itemList = document.getElementsByTagName("item");
            
            for (int i = 0; i < itemList.getLength(); i++) {
                Element item = (Element) itemList.item(i);
                String galContentId = getTextContent(item, "galContentId");
                String galContentTypeId = getTextContent(item, "galContentTypeId");
                String galTitle = getTextContent(item, "galTitle");
                String galWebImageUrl = getTextContent(item, "galWebImageUrl");
                String galCreatedtime = getTextContent(item, "galCreatedtime");
                String galModifiedtime = getTextContent(item, "galModifiedtime");
                String galPhotographyMonth = getTextContent(item, "galPhotographyMonth");
                String galPhotographyLocation = getTextContent(item, "galPhotographyLocation");
                String galPhotographer = getTextContent(item, "galPhotographer");
                String galSearchKeyword = getTextContent(item, "galSearchKeyword");
                TourPhoto photo = new TourPhoto();
                photo.setGalContentId(galContentId);
                photo.setGalContentTypeId(galContentTypeId);
                photo.setGalTitle(galTitle);
                photo.setGalWebImageUrl(galWebImageUrl);
                photo.setGalCreatedtime(galCreatedtime);
                photo.setGalModifiedtime(galModifiedtime);
                photo.setGalPhotographyMonth(galPhotographyMonth);
                photo.setGalPhotographyLocation(galPhotographyLocation);
                photo.setGalPhotographer(galPhotographer);
                photo.setGalSearchKeyword(galSearchKeyword);
                
                photos.add(photo);
            }
            
        } catch (Exception e) {
            log.error("XML 파싱 실패", e);
        }
        
        return photos;
    }
    
    /**
     * XML Element에서 텍스트 내용 추출
     */
    private String getTextContent(Element element, String tagName) {
        try {
            NodeList nodeList = element.getElementsByTagName(tagName);
            if (nodeList.getLength() > 0) {
                String content = nodeList.item(0).getTextContent();
                // UTF-8 인코딩으로 다시 디코딩 시도
                if (content != null && !content.isEmpty()) {
                    try {
                        byte[] bytes = content.getBytes("ISO-8859-1");
                        return new String(bytes, "UTF-8");
                    } catch (Exception e) {
                        return content; // 실패하면 원본 반환
                    }
                }
                return content;
            }
        } catch (Exception e) {
            // 무시하고 빈 문자열 반환
        }
        return "";
    }

}

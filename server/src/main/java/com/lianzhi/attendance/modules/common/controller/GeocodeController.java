package com.lianzhi.attendance.modules.common.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianzhi.attendance.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地理编码接口：地址 → 经纬度（给管理端办公场所地图选点用）
 * 优先调用高德 Web 服务 API（需要 key，返回 GCJ-02），降级到 OSM Nominatim（免费无 key，返回 WGS-84）
 *
 * GET /geocode?address=北京市朝阳区建国路88号&city=北京
 * → [{ "lng": 116.467526, "lat": 39.92193, "formattedAddress": "北京市朝阳区建国路88号", "source": "amap|nominatim" }]
 */
@RestController
@RequestMapping("/geocode")
public class GeocodeController {

    @Value("${amap.geocode.key:}")
    private String amapKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 地址 → 经纬度
     * @param address 必填，详细地址，如 "北京市朝阳区建国路88号"
     * @param city 选填，城市名，帮助缩小搜索范围
     */
    @GetMapping
    public Result<List<Map<String, Object>>> geocode(
            @RequestParam String address,
            @RequestParam(required = false) String city) {
        if (address == null || address.isBlank()) {
            return Result.ok(new ArrayList<>());
        }

        // 优先高德地理编码（配了 key 就用，返回 GCJ-02 直接在 Leaflet 高德瓦片上对齐）
        if (amapKey != null && !amapKey.isBlank()) {
            try {
                List<Map<String, Object>> result = geocodeAmap(address, city);
                if (!result.isEmpty()) {
                    return Result.ok(result);
                }
            } catch (Exception e) {
                // 高德失败，降级到 Nominatim
            }
        }

        // 降级：OSM Nominatim（免费无 key，返回 WGS-84，前端需要转 GCJ-02）
        try {
            List<Map<String, Object>> result = geocodeNominatim(address, city);
            return Result.ok(result);
        } catch (Exception e) {
            return Result.ok(new ArrayList<>());
        }
    }

    /** 高德 Web 服务地理编码 */
    private List<Map<String, Object>> geocodeAmap(String address, String city) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder url = new StringBuilder("https://restapi.amap.com/v3/geocode/geo?");
        url.append("key=").append(amapKey);
        url.append("&address=").append(URLEncoder.encode(address, StandardCharsets.UTF_8));
        if (city != null && !city.isBlank()) {
            url.append("&city=").append(URLEncoder.encode(city, StandardCharsets.UTF_8));
        }
        url.append("&output=json");

        String json = httpGet(url.toString());
        JsonNode root = mapper.readTree(json);
        if (!"1".equals(root.path("status").asText())) {
            return list;
        }
        JsonNode geocodes = root.path("geocodes");
        for (JsonNode g : geocodes) {
            String location = g.path("location").asText();  // "lng,lat"
            String[] parts = location.split(",");
            if (parts.length == 2) {
                Map<String, Object> item = new HashMap<>();
                item.put("lng", Double.parseDouble(parts[0]));
                item.put("lat", Double.parseDouble(parts[1]));
                item.put("formattedAddress", g.path("formatted_address").asText());
                item.put("source", "amap");
                list.add(item);
                if (list.size() >= 5) break;  // 最多返回 5 条
            }
        }
        return list;
    }

    /** OSM Nominatim 地理编码（完全免费、无 key） */
    private List<Map<String, Object>> geocodeNominatim(String address, String city) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String query = city != null && !city.isBlank() ? city + " " + address : address;
        String url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);

        String json = httpGet(url);
        JsonNode arr = mapper.readTree(json);
        for (JsonNode n : arr) {
            Map<String, Object> item = new HashMap<>();
            item.put("lng", n.path("lon").asDouble());
            item.put("lat", n.path("lat").asDouble());
            item.put("formattedAddress", n.path("display_name").asText());
            item.put("source", "nominatim");  // 前端需注意：这是 WGS-84，要转 GCJ-02
            list.add(item);
        }
        return list;
    }

    /** 简单 HTTP GET */
    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        // Nominatim 要求带 User-Agent
        conn.setRequestProperty("User-Agent", "Lianzhi-Attendance/1.0 (knowledge-base)");

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}

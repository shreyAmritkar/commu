package com.engish.style;

import java.util.LinkedHashMap;
import java.util.Map;

public record StyleDto(Long id, String key, String name, String description, String inspiredBy, Map<String, Integer> traits) {
    public static StyleDto from(StyleProfile s) {
        Map<String, Integer> t = new LinkedHashMap<>();
        t.put("warmth", s.getWarmth());
        t.put("confidence", s.getConfidence());
        t.put("directness", s.getDirectness());
        t.put("humor", s.getHumor());
        t.put("empathy", s.getEmpathy());
        t.put("formality", s.getFormality());
        return new StyleDto(s.getId(), s.getStyleKey(), s.getName(), s.getDescription(), s.getInspiredBy(), t);
    }
}

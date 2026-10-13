package com.expansion.server.global.util;

import jakarta.persistence.Id;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 페이지 정렬 보조.
 *
 * <p>정렬 값이 같은 행(좋아요 0개 작품들, 같은 시각에 만든 행 등)은 DB가 순서를 보장하지 않아
 * 페이지를 넘길 때 같은 항목이 다시 나오거나 빠질 수 있다(TROUBLESHOOTING #41).
 * 컨트롤러의 Pageable 파라미터에 {@link TieBreaker} 를 붙이면 {@code TieBreakerPageableResolver} 가
 * 이 메서드로 정렬 끝에 엔티티의 id 를 붙인다.
 */
public final class PageableUtils {

    private static final Map<Class<?>, String> ID_PROPERTY = new ConcurrentHashMap<>();

    private PageableUtils() {
    }

    /**
     * 정렬이 있으면 끝에 {@code entity} 의 id 필드를 마지막 정렬과 같은 방향으로 붙인다.
     * 정렬이 없거나 이미 id 가 들어 있으면 그대로 돌려준다.
     */
    public static Pageable withTieBreaker(Pageable pageable, Class<?> entity) {
        if (pageable.isUnpaged()) {
            return pageable;
        }
        Sort sort = pageable.getSort();
        String idProperty = idProperty(entity);
        if (sort.isUnsorted() || sort.getOrderFor(idProperty) != null) {
            return pageable;
        }
        Sort.Direction direction = sort.stream().reduce((first, second) -> second)
                .map(Sort.Order::getDirection)
                .orElse(Sort.Direction.DESC);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                sort.and(Sort.by(direction, idProperty)));
    }

    /** 엔티티의 {@code @Id} 필드 이름(상위 클래스까지 찾음). 없으면 기동 단계에서 바로 드러나도록 예외 */
    public static String idProperty(Class<?> entity) {
        return ID_PROPERTY.computeIfAbsent(entity, type -> {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(Id.class)) {
                        return f.getName();
                    }
                }
            }
            throw new IllegalArgumentException(type.getName() + " 에 @Id 필드가 없습니다(TieBreaker 대상은 엔티티여야 함).");
        });
    }
}

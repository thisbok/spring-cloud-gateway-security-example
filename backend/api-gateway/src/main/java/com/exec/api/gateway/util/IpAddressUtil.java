package com.exec.api.gateway.util;

import lombok.experimental.UtilityClass;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 🌐 IP 주소 추출 유틸리티
 * <p>
 * 다양한 프록시 환경에서 실제 클라이언트 IP 주소를 추출하는 유틸리티:
 * 1. X-Forwarded-For 헤더 우선 처리
 * 2. X-Real-IP 헤더 대안 처리
 * 3. RemoteAddress fallback 처리
 * 4. 프록시 체인을 고려한 IP 추출
 */
@UtilityClass
public class IpAddressUtil {

    /**
     * 클라이언트의 실제 IP 주소를 추출하고 정규화합니다.
     * <p>
     * 이 메서드는 IP 추출 + 정규화를 한 번에 처리합니다.
     * IPv6 loopback (0:0:0:0:0:0:0:1, ::1) → IPv4 loopback (127.0.0.1)
     *
     * @param request HTTP 요청 객체
     * @return 정규화된 클라이언트 IP 주소 (추출 실패 시 "unknown")
     */
    public static String getClientIpAddressNormalized(ServerHttpRequest request) {
        String rawIp = getClientIpAddress(request);
        return normalizeIpAddress(rawIp);
    }

    /**
     * 클라이언트의 실제 IP 주소를 추출합니다 (정규화 없음).
     * <p>
     * 우선순위:
     * 1. X-Forwarded-For (첫 번째 IP)
     * 2. X-Real-IP
     * 3. X-Original-Forwarded-For
     * 4. X-Client-IP
     * 5. CF-Connecting-IP (Cloudflare)
     * 6. RemoteAddress
     *
     * @param request HTTP 요청 객체
     * @return 클라이언트 IP 주소 (추출 실패 시 "unknown")
     */
    public static String getClientIpAddress(ServerHttpRequest request) {
        // 1. X-Forwarded-For 헤더에서 확인 (가장 일반적)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (isValidIp(xForwardedFor)) {
            // 첫 번째 IP 가 실제 클라이언트 IP (프록시 체인에서)
            return xForwardedFor.split(",")[0].trim();
        }

        // 2. X-Real-IP 헤더에서 확인 (Nginx 등에서 사용)
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (isValidIp(xRealIp)) {
            return xRealIp.trim();
        }

        // 3. X-Original-Forwarded-For 헤더에서 확인
        String xOriginalForwardedFor = request.getHeaders().getFirst("X-Original-Forwarded-For");
        if (isValidIp(xOriginalForwardedFor)) {
            return xOriginalForwardedFor.split(",")[0].trim();
        }

        // 4. X-Client-IP 헤더에서 확인 (일부 프록시에서 사용)
        String xClientIp = request.getHeaders().getFirst("X-Client-IP");
        if (isValidIp(xClientIp)) {
            return xClientIp.trim();
        }

        // 5. CF-Connecting-IP 헤더에서 확인 (Cloudflare CDN)
        String cfConnectingIp = request.getHeaders().getFirst("CF-Connecting-IP");
        if (isValidIp(cfConnectingIp)) {
            return cfConnectingIp.trim();
        }

        // 6. 직접 연결인 경우 RemoteAddress 에서 추출
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * IP 주소가 유효한지 검증합니다.
     *
     * @param ip 검증할 IP 주소
     * @return 유효한 IP 인 경우 true
     */
    private static boolean isValidIp(String ip) {
        return ip != null &&
                !ip.isEmpty() &&
                !ip.equalsIgnoreCase("unknown") &&
                !ip.equalsIgnoreCase("127.0.0.1") &&
                !ip.equalsIgnoreCase("0:0:0:0:0:0:0:1") &&
                !ip.equalsIgnoreCase("::1");
    }

    /**
     * 클라이언트 IP 주소를 가져와서 로깅용으로 포맷팅합니다.
     *
     * @param request HTTP 요청 객체
     * @return 로깅에 적합한 형태의 IP 주소
     */
    public static String getClientIpForLogging(ServerHttpRequest request) {
        String clientIp = getClientIpAddress(request);

        // IPv6 주소인 경우 축약 형태로 변환
        if (clientIp.contains(":") && !clientIp.equals("unknown")) {
            // IPv6 주소를 더 간결하게 표시
            return "[" + clientIp + "]";
        }

        return clientIp;
    }

    /**
     * 전체 프록시 체인 정보를 가져옵니다 (디버깅용).
     *
     * @param request HTTP 요청 객체
     * @return 프록시 체인 정보
     */
    public static String getFullProxyChain(ServerHttpRequest request) {
        StringBuilder proxyChain = new StringBuilder();

        proxyChain.append("X-Forwarded-For: ").append(request.getHeaders().getFirst("X-Forwarded-For")).append("; ");
        proxyChain.append("X-Real-IP: ").append(request.getHeaders().getFirst("X-Real-IP")).append("; ");
        proxyChain.append("X-Original-Forwarded-For: ").append(request.getHeaders().getFirst("X-Original-Forwarded-For")).append("; ");
        proxyChain.append("CF-Connecting-IP: ").append(request.getHeaders().getFirst("CF-Connecting-IP")).append("; ");

        if (request.getRemoteAddress() != null) {
            proxyChain.append("RemoteAddress: ").append(request.getRemoteAddress().getAddress().getHostAddress());
        }

        return proxyChain.toString();
    }

    /**
     * IP 주소가 특정 CIDR 범위에 속하는지 확인합니다.
     *
     * @param ip   확인할 IP 주소
     * @param cidr CIDR 표기법 (예: "192.168.1.0/24")
     * @return CIDR 범위에 속하면 true
     */
    public static boolean isIpInCidrRange(String ip, String cidr) {
        try {
            String[] cidrParts = cidr.split("/");
            if (cidrParts.length != 2) {
                return false;
            }

            String networkIp = cidrParts[0];
            int prefixLength = Integer.parseInt(cidrParts[1]);

            // 간단한 IPv4 CIDR 체크 (실제 운영시에는 Apache Commons Net 등 사용 권장)
            String[] ipParts = ip.split("\\.");
            String[] networkParts = networkIp.split("\\.");

            if (ipParts.length != 4 || networkParts.length != 4) {
                return false;
            }

            long ipLong = ipToLong(ip);
            long networkLong = ipToLong(networkIp);
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;

            return (ipLong & mask) == (networkLong & mask);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * IPv4 주소를 long 값으로 변환합니다.
     */
    private static long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) + Integer.parseInt(parts[i]);
        }
        return result;
    }

    /**
     * IP 주소가 사설 IP 범위인지 확인합니다.
     *
     * @param ip 확인할 IP 주소
     * @return 사설 IP 인 경우 true
     */
    public static boolean isPrivateIp(String ip) {
        if (ip == null || ip.equals("unknown")) {
            return false;
        }

        return isIpInCidrRange(ip, "10.0.0.0/8") ||
                isIpInCidrRange(ip, "172.16.0.0/12") ||
                isIpInCidrRange(ip, "192.168.0.0/16") ||
                ip.equals("127.0.0.1") ||
                ip.equals("::1");
    }

    /**
     * IP 주소를 정규화합니다.
     * <p>
     * IPv6 loopback (0:0:0:0:0:0:0:1, ::1) → 127.0.0.1
     * IPv4 loopback (127.0.0.1) → 127.0.0.1
     * 기타 IPv6 → 축약형으로 변환
     *
     * @param ip 정규화할 IP 주소
     * @return 정규화된 IP 주소
     */
    public static String normalizeIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }

        // IPv6 loopback 주소를 IPv4 loopback 으로 정규화
        if (ip.equalsIgnoreCase("0:0:0:0:0:0:0:1") || ip.equalsIgnoreCase("::1")) {
            return "127.0.0.1";
        }

        // IPv6 주소의 경우 축약형으로 변환 (간단한 처리)
        if (ip.contains(":")) {
            // 연속된 0 그룹을 :: 으로 축약 (간단한 버전)
            return ip.replaceAll("(^|:) 0+(:|$)", "$1$2")
                    .replaceAll(":{2,}", "::")
                    .toLowerCase();
        }

        return ip;
    }

    /**
     * 두 IP 주소가 동일한지 비교합니다 (정규화 후 비교).
     * <p>
     * 127.0.0.1 == 0:0:0:0:0:0:0:1 == ::1 (모두 loopback)
     *
     * @param ip1 첫 번째 IP 주소
     * @param ip2 두 번째 IP 주소
     * @return 동일한 IP 인 경우 true
     */
    public static boolean isSameIp(String ip1, String ip2) {
        if (ip1 == null || ip2 == null) {
            return false;
        }

        String normalizedIp1 = normalizeIpAddress(ip1);
        String normalizedIp2 = normalizeIpAddress(ip2);

        return normalizedIp1.equalsIgnoreCase(normalizedIp2);
    }
}
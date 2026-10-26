package com.lianzhi.attendance.modules.qrcode.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lianzhi.attendance.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 二维码生成接口（ZXing 库）
 * GET /qrcode/venue/{venueId}       → 返回 PNG 图片，前端 <img src="/api/qrcode/venue/1"> 直接显示
 * GET /qrcode/venue/{venueId}/url   → 返回二维码里的网址（管理端弹窗里显示出来方便核对）
 *
 * 二维码内容：真实网址 http://电脑局域网IP:5173/h5?venueId=xx
 * 手机相机扫码 → 直接打开 H5 入口页（采集 / 打卡两个按钮），员工不用手输网址
 *
 * IP 怎么来的：默认自动探测本机局域网 IPv4；
 * 如果探测不准，可以在 application.yml 里配置 lianzhi.h5-base 手动指定。
 */
@RestController
@RequestMapping("/qrcode")
public class QrCodeController {

    /** 手机前端访问地址（vite 开发服务器跑在 5173，不是后端 8080） */
    private static final String H5_PORT = "5173";

    /** 可选：手动指定 H5 地址，例如 http://172.20.10.7:5173；不填就自动探测 */
    @Value("${lianzhi.h5-base:}")
    private String h5Base;

    @GetMapping("/venue/{venueId}")
    public void venueQrCode(@PathVariable Long venueId, HttpServletResponse response) throws Exception {
        // 二维码里放真实网址，手机扫码能直接打开
        String content = venueUrl(venueId);
        // 生成 300x300 的二维码
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300);
        response.setContentType("image/png");
        MatrixToImageWriter.writeToStream(matrix, "PNG", response.getOutputStream());
    }

    /** 给管理端弹窗用：把二维码里的网址以文字形式返回，方便确认手机能不能访问 */
    @GetMapping("/venue/{venueId}/url")
    public Result<String> venueUrlApi(@PathVariable Long venueId) {
        return Result.ok(venueUrl(venueId));
    }

    /** 拼接场所码网址：http://IP:5173/h5?venueId=xx */
    private String venueUrl(Long venueId) {
        return h5Base() + "/h5?venueId=" + venueId;
    }

    /** 取 H5 访问根地址：优先用配置，没配置就自动探测局域网 IP */
    private String h5Base() {
        String base = h5Base;
        if (base == null || base.trim().isEmpty()) {
            base = "http://" + lanIp() + ":" + H5_PORT;
        }
        base = base.trim();
        // 去掉末尾多余的斜杠，避免拼成 http://ip:5173//h5
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * 自动探测本机局域网 IPv4 地址
     * 跳过：回环地址、未启用的网卡、虚拟网卡（VMware/VirtualBox/Hyper-V/WSL），
     * 这些网卡的 IP 手机是访问不到的
     */
    private String lanIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                // Windows 上网卡有两个名字：getName() 是 eth0 这种短名，
                // getDisplayName() 才是「VMware Network Adapter VMnet1」这种中文/友好名，
                // 所以要两个都拿来判断，把虚拟网卡排除掉（手机访问不到虚拟网卡的 IP）
                String name = ((ni.getName() == null ? "" : ni.getName()) + " "
                        + (ni.getDisplayName() == null ? "" : ni.getDisplayName())).toLowerCase();
                if (name.contains("vmnet") || name.contains("vmware") || name.contains("virtual")
                        || name.contains("vbox") || name.contains("hyper-v") || name.contains("vethernet")
                        || name.contains("docker") || name.contains("tap") || name.contains("tun")) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    // isSiteLocalAddress：192.168.x.x / 10.x.x.x / 172.16-31.x.x
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // 探测失败就走下面的兜底
        }
        return "localhost";
    }
}

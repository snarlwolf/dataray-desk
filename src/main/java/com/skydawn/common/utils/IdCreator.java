package com.skydawn.common.utils;

import org.agrona.concurrent.IdGenerator;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.huxhorn.sulky.ulid.ULID;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class IdCreator {

    private static final Logger log = LoggerFactory.getLogger(IdCreator.class);
    
    // ULID 生成器单例（线程安全）
    private static final ULID ULID_GEN = new ULID();
    
    // Snowflake ID 生成器单例（线程安全）
    private static final IdGenerator SNOWFLAKE_GENERATOR;
    
    static {
        // 初始化 Snowflake 生成器，使用设备相关的 nodeId
        long nodeId = computeNodeId();
        SNOWFLAKE_GENERATOR = new SnowflakeIdGenerator(nodeId);
        log.info("SnowflakeIdGenerator 已初始化，nodeId: {}", nodeId);
    }

	public static String createUUID() {
		return ULID_GEN.nextValue().toString();
	}
	
	public static Long createSnowId() {
        return SNOWFLAKE_GENERATOR.nextId();
	}

	/** 雪花 ID 字符串形式，用于 conversationId 等业务主键 */
	public static String createSnowIdStr() {
		return String.valueOf(SNOWFLAKE_GENERATOR.nextId());
	}
	
	/**
	 * 计算节点ID（0-1023，Snowflake 使用10位）
	 * 基于设备信息生成，确保不同实例有不同的 nodeId
	 */
	private static long computeNodeId() {
        // 1. 优先使用环境变量（如果配置了）
        String envNodeId = System.getenv("SNOWFLAKE_NODE_ID");
        if (envNodeId != null && !envNodeId.isEmpty()) {
            try {
                long nodeId = Long.parseLong(envNodeId);
                if (nodeId >= 0 && nodeId <= 1023) {
                    log.info("使用环境变量 SNOWFLAKE_NODE_ID: {}", nodeId);
                    return nodeId;
                } else {
                    log.warn("环境变量 SNOWFLAKE_NODE_ID 值 {} 超出范围 [0-1023]，将使用自动计算", nodeId);
                }
            } catch (NumberFormatException e) {
                log.warn("环境变量 SNOWFLAKE_NODE_ID 格式错误: {}，将使用自动计算", envNodeId);
            }
        }
        
        // 2. 基于设备信息计算 nodeId
        try {
            // 获取本机IP地址的hash
            long ipHash = getLocalIpHash();
            
            // 获取MAC地址的hash（如果可用）
            long macHash = getMacAddressHash();
            
            // 获取主机名的hash
            long hostnameHash = getHostnameHash();
            
            // 组合多个因素生成 nodeId
            long combined = (ipHash ^ macHash ^ hostnameHash) & 0x3FF; // 取低10位 (0-1023)
            
            // 确保不为0（0通常保留）
            if (combined == 0) {
                combined = 1;
            }
            
            log.info("自动计算 nodeId: {} (基于 IP/MAC/Hostname)", combined);
            return combined;
            
        } catch (Exception e) {
            log.warn("计算 nodeId 时发生异常，使用默认值 1: {}", e.getMessage());
            return 1L;
        }
    }
    
    /**
     * 获取本机IP地址的hash值
     */
    private static long getLocalIpHash() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            byte[] addr = localhost.getAddress();
            long hash = 0;
            for (byte b : addr) {
                hash = hash * 31 + (b & 0xFF);
            }
            return Math.abs(hash);
        } catch (Exception e) {
            log.debug("获取本机IP失败: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 获取MAC地址的hash值
     */
    private static long getMacAddressHash() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    long hash = 0;
                    for (byte b : mac) {
                        hash = hash * 31 + (b & 0xFF);
                    }
                    return Math.abs(hash);
                }
            }
        } catch (SocketException e) {
            log.debug("获取MAC地址失败: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * 获取主机名的hash值
     */
    private static long getHostnameHash() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return Math.abs(hostname.hashCode());
        } catch (Exception e) {
            log.debug("获取主机名失败: {}", e.getMessage());
            return 0;
        }
    }
}

package com.qunhui.chen.fullstacktest.adapter.jobs

import com.qunhui.chen.fullstacktest.service.ProductSyncService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * @author Qunhui Chen
 * @date 2025/9/24 17:00
 */
@Component
class ProductSyncJob(
    private val productSyncService: ProductSyncService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 按要求：initialDelay=0，应用启动后立即跑一次*/
    @Scheduled(initialDelay = 0, fixedDelay = 90000_000)
    fun run() {
        try {
            productSyncService.sync()
        } catch (e: Exception) {
            // 避免异常冒泡导致调度器中断
            log.error("Scheduled sync failed", e)
        }
    }
}
package com.qunhui.chen.fullstacktest.adapter.web.controller;

import com.qunhui.chen.fullstacktest.repo.ProductRepo
import com.qunhui.chen.fullstacktest.repo.ProductUpsertCmd
import com.qunhui.chen.fullstacktest.repo.VariantRepo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

/**
 * @author Qunhui Chen
 * @date 2025/9/24 00:58
 */
@Controller
class ProductController(
    private val productRepo: ProductRepo,
    private val variantRepo: VariantRepo,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/")
    fun index(model: Model): String = "index"

    /**
     * 返回 <tbody id="products"> 片段，供 htmx 替换
     */
    @GetMapping("/products")
    fun productsPartial(
        @RequestParam(name = "search", required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        model: Model
    ): String {
        val total = productRepo.countForView(search)
        val totalPages = if (total == 0L) 1 else ((total + size - 1) / size).toInt()
        val p = page.coerceIn(1, totalPages)
        val offset = (p - 1) * size

        val items = productRepo.listForViewPaged(limit = size, offset = offset, search = search)

        model.addAttribute("items", items)
        model.addAttribute("page", p)
        model.addAttribute("size", size)
        model.addAttribute("total", total)
        model.addAttribute("totalPages", totalPages)

        return "products/_tbody :: products_tbody"
    }

    @GetMapping("/products/{productId}/variants")
    fun variantsPartial(@PathVariable productId: Long, model: Model): String {
        val rows = variantRepo.listByProductId(productId)
        val optionNames = productRepo.loadOptionNames(productId)
        model.addAttribute("productId", productId)
        model.addAttribute("rows", rows)
        model.addAttribute("optionNames", optionNames)
        return "products/_variants :: variants_rows"
    }

    /**
     * 删除：先删 variants，再删 product（我们当前没有 FK，需要手工保证一致性）
     */
    @DeleteMapping("/products/{productId}")
    @ResponseBody
    fun delete(@PathVariable productId: Long):

            String {
        variantRepo.deleteVariantsNotInProducts(listOf(productId), minGuard = 1) // 只删这个 productId 之外的不会动；我们单独执行：
        // 单独写一个“按 productId 删除 variants”的方法更直接（见下方 repo 补充）
        variantRepo.deleteByProductId(productId)
        productRepo.deleteByProductId(productId)
        log.info("Deleted product={} and its variants", productId)
        return "" // 返回空串，htmx 会把目标元素置空（outerHTML）
    }
}


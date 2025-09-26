package com.qunhui.chen.fullstacktest.adapter.web.controller;

import com.qunhui.chen.fullstacktest.adapter.web.domain.CreateProductForm
import com.qunhui.chen.fullstacktest.repo.ProductRepo
import com.qunhui.chen.fullstacktest.repo.VariantRepo
import com.qunhui.chen.fullstacktest.service.product.ProductService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*

/**
 * - GET   "/"                         → 首页
 * - GET   "/products"                 → 返回 <tbody id="products"> 片段
 * - GET   "/products/form"            → 返回创建产品表单片段
 * - POST  "/addProducts"              → 处理创建（沿用原路由），成功后刷新 #products
 * - GET   "/products/{productId}/variants" → 返回某产品的变体行片段
 *
 * @author Qunhui Chen
 * @date 2025/9/24 00:58
 */
@Controller
class ProductController(
    private val productRepo: ProductRepo,
    private val variantRepo: VariantRepo,
    private val productService: ProductService,

    ) {

    // 首页
    @GetMapping("/")
    fun index(model: Model): String = "index"

    // 返回 <tbody id="products"> 片段（分页/搜索）
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
        model.addAttribute("search", search)
        return "products/_tbody :: products_tbody"
    }

    // 表单片段
    @GetMapping("/products/form")
    fun form(model: Model): String = "products/_form :: create_form"

    // 创建 & 刷新表格（沿用原路由 /addProducts，避免前端 htmx 改动）
    @PostMapping("/addProducts")
    fun create(
        @Valid @ModelAttribute form: CreateProductForm,
        result: BindingResult,
        model: Model,
        response: HttpServletResponse
    ): String {
        if (result.hasErrors()) {
            model.addAttribute("errors", result)
            model.addAttribute("form", form)
            return "products/_form :: create_form"
        }

        productService.addProduct(form)

        val rows = productService.listFirstPage(10)

        model.addAttribute("items", rows)
        response.setHeader("HX-Retarget", "#products")
        response.setHeader("HX-Reswap", "outerHTML")
        response.setHeader("HX-Trigger", "product:created")
        return "products/_tbody :: products_tbody"
    }

    // 变体行片段
    @GetMapping("/products/{productId}/variants")
    fun variantsPartial(@PathVariable productId: Long, model: Model): String {
        val rows = variantRepo.listByProductId(productId)
        val optionNames = productRepo.loadOptionNames(productId)
        model.addAttribute("productId", productId)
        model.addAttribute("rows", rows)
        model.addAttribute("optionNames", optionNames)
        return "products/_variants :: variants_rows"
    }
}


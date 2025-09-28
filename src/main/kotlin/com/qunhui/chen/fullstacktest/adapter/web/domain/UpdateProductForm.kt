package com.qunhui.chen.fullstacktest.adapter.web.domain

import java.math.BigDecimal

data class UpdateProductForm(
    var productId: Long = 0,
    var title: String = "",
    var vendor: String? = null,
    var productType: String? = null,
    var tagsText: String? = null,
    var optionNames: MutableList<String> = mutableListOf(),
    var variants: MutableList<UpdateVariantForm> = mutableListOf()
)

data class UpdateVariantForm(
    var variantId: Long = 0,
    var sku: String? = null,
    var imageUrl: String? = null,
    var price: BigDecimal? = null,
    var comparePrice: BigDecimal? = null,
    var available: Boolean? = null,
    var option1: String? = null,
    var option2: String? = null,
    var option3: String? = null
)


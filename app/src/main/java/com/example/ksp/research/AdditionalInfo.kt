package com.example.ksp.research

import com.example.ksp.fieldprocessor.HasMetadata
import com.example.ksp.fieldprocessor.MetaField

@HasMetadata
data class AdditionalInfo(
    @MetaField("Name")
    val string: String,
    @MetaField("Amount")
    val int: Int,
    @MetaField("Weight")
    val float: Float,
)
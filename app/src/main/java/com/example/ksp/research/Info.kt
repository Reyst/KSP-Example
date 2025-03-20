package com.example.ksp.research

import com.example.ksp.fieldprocessor.CustomFields
import com.example.ksp.fieldprocessor.HasMetadata
import com.example.ksp.fieldprocessor.MetaField

@HasMetadata
data class Info(
    @MetaField("Name")
    val s: String,

    @MetaField("ID")
    val i: Int,

    @MetaField("ToTaL")
    val f: Float,

    @CustomFields
    val cf: Map<String, String> = emptyMap(),
)

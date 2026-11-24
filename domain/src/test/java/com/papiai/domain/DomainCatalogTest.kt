package com.papiai.domain

import com.papiai.domain.model.ModelCatalogRegistry
import com.papiai.domain.model.PapiStrings
import com.papiai.domain.model.SupportedLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainCatalogTest {

  @Test
  fun catalogOnlyContainsVerifiedFossModels() {
    val allowedLicenses = setOf("Apache-2.0", "MIT")
    assertTrue(ModelCatalogRegistry.allModels.isNotEmpty())
    ModelCatalogRegistry.allModels.forEach { model ->
      assertTrue("${model.id} has unexpected license ${model.licenseName}", model.licenseName in allowedLicenses)
      assertTrue("${model.id} missing files", model.files.isNotEmpty())
      model.files.forEach { file ->
        assertTrue(file.downloadUrl.startsWith("https://"))
        assertFalse(file.downloadUrl.contains("googleapis.com"))
      }
    }
  }

  @Test
  fun localizationSupportsEnglishHindiOdia() {
    val en = PapiStrings.forLanguage(SupportedLanguage.ENGLISH)
    val hi = PapiStrings.forLanguage(SupportedLanguage.HINDI)
    val or = PapiStrings.forLanguage(SupportedLanguage.ODIA)
    assertTrue(en.navHome.isNotBlank())
    assertTrue(hi.navHome != en.navHome)
    assertTrue(or.navHome != en.navHome)
  }
}

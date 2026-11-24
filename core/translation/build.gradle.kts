plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.papiai.core.translation"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":domain"))
  implementation(project(":core:ai"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.onnxruntime.android)
  implementation(libs.okhttp)
  testImplementation(libs.junit)
}

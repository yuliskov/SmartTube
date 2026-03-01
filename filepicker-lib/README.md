# Material File Picker by Arte al Programar

Material file picker library for Android by Arte al Programar

![](ss/main.png)

## What's new

- Require Android Jelly Bean 4.1.x (API 16+)
- Material You (Dynamics Color) Support
- Night Mode Support
- New Icon Designs

## Add your project

Using Jcenter

```
build.gradle (Project)

allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}


build.gradle (Module: app)

dependencies {
    ...
    // Java
    implementation 'androidx.activity:activity:1.4.0'
    implementation 'androidx.fragment:fragment:1.4.1'

    // Kotlin
    implementation 'androidx.activity:activity-ktx:1.4.0'
    implementation 'androidx.fragment:fragment-ktx:1.4.1'
    implementation 'com.github.arteaprogramar:Android-MaterialFilePicker:3.0.1'
}


```

## Using (IMPORTANT)

- For Android 11 and above, you must request "MANAGE_EXTERNAL_STORAGE" permission in your
  application, "Material File Picker" requires that permission to read and show user files.

- Open your class and add the following code

```
...
kotlin 

/** 
 *  This library require "Activity Result" API 
 **/

private val startForResultFiles = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result: ActivityResult ->
    onActivityResult(result.resultCode, result.data)
} 
 
...

// External Storage Path
val externalStorage = FileUtils.getFile(applicationContext, null)

MaterialFilePicker()
        // Pass a source of context. Can be:
        //    .withActivity(Activity activity)
        //    .withFragment(Fragment fragment)
        //    .withSupportFragment(androidx.fragment.app.Fragment fragment)
        .withActivity(this)
        // With cross icon on the right side of toolbar for closing picker straight away
        .withCloseMenu(true)
        // Entry point path (user will start from it)
        //.withPath(alarmsFolder.absolutePath)
        // Root path (user won't be able to come higher than it)
        .withRootPath(externalStorage.absolutePath)
        // Showing hidden files
        .withHiddenFiles(true)
        // Want to choose only jpg images
        .withFilter(Pattern.compile(".*\\.(jpg|jpeg)$"))
        // Don't apply filter to directories names
        .withFilterDirectories(false)
        .withTitle("Sample title")
        // Require "Activity Result" API
        .withActivityResultApi(startForResultFiles)
        .start()
...


/** 
 *  For Android API 29+, You need 
 *  <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" /> 
 * And some extra settings.
 * You can check the demo of the application
 **/


```

Override on activity result:

```
kotlin

private fun onActivityResult(resultCode: Int, data: Intent?) {
    if (resultCode == Activity.RESULT_OK) {
        val path: String? = data?.getStringExtra(FilePickerActivity.RESULT_FILE_PATH)

        if (path != null) {
            Log.d("Path: ", path)
            Toast.makeText(this, "Picked file: $path", Toast.LENGTH_LONG).show()
        }
    }
}

```
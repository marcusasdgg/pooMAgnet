package com.example.poomagnet.downloadService

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

const val APPFOLDERNAME = "pooMAgnet"

public class DownloadService @Inject constructor(@ApplicationContext val context: Context) {

    private var pooMagFolderURI: Uri? = null //potenitally wont be used.
    private val resolver: ContentResolver


    private val apiService = DownloadRetrofitInstance.api

    init {
        resolver = context.contentResolver
        updateRootURI()
    }

    private fun updateRootURI(){
        val folderDirectory = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(APPFOLDERNAME)

        val projection = arrayOf(
            MediaStore.Images.Media._ID, // The ID of the image
            MediaStore.Images.Media.DISPLAY_NAME, // The display name (file name)
            MediaStore.Images.Media.RELATIVE_PATH // The relative path to the file (in this case, "Pictures/")
        )
        resolver.query(folderDirectory, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                // Get the ID of the image file
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)

                // Create the URI for the image using the ID
                pooMagFolderURI = Uri.withAppendedPath(folderDirectory, id.toString())

            } else {
            }
        }
    }

    private suspend fun addImageToFolder(folderName: String, imageName: String, imageUrl: String, refererUrl: String = "", mangaId: String): Uri?{
        val customDate = 946684800000

        //check if iamge exists first.
        val folderDirectory = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(imageName, "%${Environment.DIRECTORY_PICTURES}/$APPFOLDERNAME/$mangaId%")

        val projection = arrayOf(
            MediaStore.Images.Media._ID, // The ID of the image
            MediaStore.Images.Media.DISPLAY_NAME, // The display name (file name)
            MediaStore.Images.Media.RELATIVE_PATH // The relative path to the file (in this case, "Pictures/")
        )

        resolver.query(folderDirectory, projection, selection, selectionArgs, sortOrder)?.use {
            cursor ->
            if (cursor.moveToFirst()){
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)

                // Create the URI for the image using the ID
                return Uri.withAppendedPath(folderDirectory, id.toString())
            }

        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$imageName.jpeg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$APPFOLDERNAME/$folderName")
            put(MediaStore.Images.Media.DATE_TAKEN, customDate)
            put(MediaStore.Images.Media.DATE_ADDED, customDate / 1000) // DATE_ADDED expects seconds
            put(MediaStore.Images.Media.DATE_MODIFIED, customDate / 1000) // DATE_MODIFIED expects seconds
        }


        //Log.d("TAG", "addImageToFolder: sending request to $imageUrl")
        val image = downloadImage(imageUrl, refererUrl)

        if (image == null){
            Log.d("TAG", "addImageToFolder: download fialed")
            return null
        }



        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri.let {
            if (it != null) {
                resolver.openOutputStream(it)?.use { outputStream ->
                   image.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    return uri
                }
            }
        }
        return null
    }

    private suspend fun downloadImage(url: String, refererUrl: String =""): Bitmap?{
        try {
            val response = apiService.downloadFile(url, if (refererUrl == "") null else refererUrl)
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val inputStream = responseBody.byteStream()
                    return BitmapFactory.decodeStream(inputStream)
                }
            } else {
                return null
            }
        } catch (e: Exception) {
            return null
        }

        return null
    }


    suspend fun downloadCoverUrl(mangaId: String, url: String): String {
        val image = addImageToFolder(mangaId, mangaId, url, mangaId = mangaId)
        if (image !== null){
            return "$mangaId.jpeg"
        } else {
            return ""
        }
    }


    suspend fun downloadContent(mangaId: String, chapterId: String, url: String, refererUrl: String = ""): String{
        val image = addImageToFolder("$mangaId/$chapterId",url.split("/").last().substringBeforeLast("."), url, refererUrl, mangaId)
        if (image !== null){
//            Log.d("TAG", "downloadContent: storing image name as ${
//                url.split("/").last()}")
            return url.split("/").last().substringBeforeLast(".")+".jpeg"
        } else {
            return ""
        }
    }

    suspend fun retrieveMangaImage(mangaId: String, chapterId: String, name: String) : Uri?{
        val folderDirectory = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(name, "%${Environment.DIRECTORY_PICTURES}/$APPFOLDERNAME/$mangaId/$chapterId%")

        val projection = arrayOf(
            MediaStore.Images.Media._ID, // The ID of the image
            MediaStore.Images.Media.DISPLAY_NAME, // The display name (file name)
            MediaStore.Images.Media.RELATIVE_PATH // The relative path to the file (in this case, "Pictures/")
        )

        resolver.query(folderDirectory, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                // Get the ID of the image file
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                val relativePathColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val relativePath = cursor.getString(relativePathColumn)

                if (relativePath.endsWith("$mangaId/$chapterId/")) {
                    return Uri.withAppendedPath(folderDirectory, id.toString())
                }
            }
        }
        Log.d("TAG", "retrieveImage: no image found with name $name")
        return null
    }

     fun retrieveImage(mangaId: String, imageName: String): Uri?{
        val folderDirectory = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(imageName, "%${Environment.DIRECTORY_PICTURES}/$APPFOLDERNAME/$mangaId%")

        val projection = arrayOf(
            MediaStore.Images.Media._ID, // The ID of the image
            MediaStore.Images.Media.DISPLAY_NAME, // The display name (file name)
            MediaStore.Images.Media.RELATIVE_PATH // The relative path to the file (in this case, "Pictures/")
        )

        resolver.query(folderDirectory, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                // Get the ID of the image file
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)

                // Create the URI for the image using the ID
                return Uri.withAppendedPath(folderDirectory, id.toString())
            } else {
                Log.d("TAG", "retrieveImage: no image found with name $imageName in path ${Environment.DIRECTORY_PICTURES}/$APPFOLDERNAME/$mangaId")
                return null
            }
        }
        Log.d("TAG", "retrieveImage: no image found with name $imageName")
        return null

    }

    fun checkDownloaded(mangaId: String, chapterId: String): List<String> {
        //query the mangaid/chapterid for how many images in folder, if doesn't exist return 0.
        val folderDirectory = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%${Environment.DIRECTORY_PICTURES}/$APPFOLDERNAME/$mangaId/$chapterId%")
        val projection = arrayOf(
            MediaStore.Images.Media._ID, // The ID of the image
            MediaStore.Images.Media.DISPLAY_NAME, // The display name (file name)
        )

        val list = mutableListOf<String>()
        resolver.query(folderDirectory, projection, selection, selectionArgs, sortOrder)?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (c.moveToNext()) { // Move first before accessing data
                list.add(c.getString(idColumn))
            }
        }
        if (list.isNotEmpty()){
            Log.d("TAG", "checkDownloaded: $chapterId has downloaded images")
        } else {
            Log.d("TAG", "checkDownloaded: $chapterId is online")
        }
        return list.sorted()
    }




}

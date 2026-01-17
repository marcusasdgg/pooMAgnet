package com.example.poomagnet.comickService

import android.content.Context
import android.util.Log
import com.example.poomagnet.downloadService.DownloadService
import com.example.poomagnet.mangaRepositoryManager.Chapter
import com.example.poomagnet.mangaRepositoryManager.ChapterContents
import com.example.poomagnet.mangaRepositoryManager.ChapterContentsAdapter
import com.example.poomagnet.mangaRepositoryManager.ContentRating
import com.example.poomagnet.mangaRepositoryManager.DemoTypeAdapter
import com.example.poomagnet.mangaRepositoryManager.Demographic
import com.example.poomagnet.mangaRepositoryManager.MangaInfo
import com.example.poomagnet.mangaRepositoryManager.Ordering
import com.example.poomagnet.mangaRepositoryManager.SimpleDate
import com.example.poomagnet.mangaRepositoryManager.SimpleDateAdapter
import com.example.poomagnet.mangaRepositoryManager.SlimChapter
import com.example.poomagnet.mangaRepositoryManager.SlimChapterAdapter
import com.example.poomagnet.mangaRepositoryManager.Sources
import com.example.poomagnet.mangaRepositoryManager.Tag
import com.example.poomagnet.mangaRepositoryManager.isOnline
import com.example.poomagnet.mangaRepositoryManager.mangaState
import com.example.poomagnet.ui.SearchScreen.Direction
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

data class BackUpInstance (
    val library: MutableList<MangaInfo>,
    var newUpdatedChapters: MutableList<Pair<SimpleDate, SlimChapter>>,
    val tagMap: MutableMap<Tag, String>,
    val demoMap: MutableMap<Demographic, Int>,
    val reversedGenreMap: MutableMap<Int,Tag>,
    val sortMap: MutableMap<Ordering, String>,
    val idSet: MutableSet<String>,
)

// essence of a repository is that
// it provides a few features:
// abstracts a lot of the manga searching and offline reading from teh viewmodel
// some of teh most important functions we need are to be able to search for manga based on tags
// download chapters, add chapters to library.
class ComickRepository @Inject constructor(val context: Context, private val downloadService: DownloadService) {
    private val apiService = retrofitInstance.api
    private val basePictureUrl = "https://meo.comick.pictures"
    var newUpdatedChapters: MutableList<Pair<SimpleDate, SlimChapter>> = mutableListOf()
    // comicks api works interestingly
    // tags work using a string instead of int like manganato

    private var tagMap: MutableMap<Tag, String> = mutableMapOf()
    private var demoMap: MutableMap<Demographic, Int> = mutableMapOf()
    private var reversedGenreMap: MutableMap<Int, Tag> = mutableMapOf()
    private var sortMap: MutableMap<Ordering, String> = mutableMapOf()
    private val noDemo = 5
    var library: MutableList<MangaInfo> = mutableListOf()
    private var idSet: MutableSet<String> = mutableSetOf()


    // deserializer/serializer
    private val gsonSerializer = GsonBuilder()
        .registerTypeAdapter(ChapterContents::class.java, ChapterContentsAdapter())
        .registerTypeAdapter(SimpleDate::class.java, SimpleDateAdapter())
        .registerTypeAdapter(SlimChapterAdapter::class.java, SlimChapterAdapter())
        .registerTypeAdapter(Demographic::class.java, DemoTypeAdapter())
        .create()


    init {
        // call restoreBackup
        loadMangaFromBackup(context)
        setupTags()

    }


    private fun setupTags() {
        Log.d("TAG", "setupTags: starting up tag initialisation")
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("TAG", "Coroutine actually started!")
            try {
                val genreList = apiService.getGenreList()
                for (genre in genreList) {
                    val name: String = genre["name"].toString()
                    val slug = genre["slug"]?.toString() ?: continue
                    val id = genre["id"].toString()
                    val intId = id.toDoubleOrNull()?.toInt() ?: continue

                    // check corresponding tag in tags

                    val tag = when (name.lowercase()){
                        "yuri" -> Tag.Girls_Love
                        "yaoi" -> Tag.Boys_Love
                        else -> Tag.fromValue(name) ?: continue
                    }
                    tagMap[tag] = slug
                    reversedGenreMap[intId] = tag
                }

                Log.d(
                    "TAG",
                    "setupTags: found ${tagMap.size} tags of the ${Tag.entries.size} total "
                )
                Log.d(
                    "TAG",
                    "setupTags: tag mapping is $tagMap "
                )
            } catch (e: Exception) {
                Log.e("TAG", "setupTags: comcik repo failed", e)
            }
        }
        demoMap[Demographic.Shounen] = 1
        demoMap[Demographic.Shoujo] = 2
        demoMap[Demographic.Seinen] = 3
        demoMap[Demographic.Josei] = 4

        sortMap[Ordering.Followed_Count] = "user_follow_count"
        sortMap[Ordering.Year] = "created_at"
        sortMap[Ordering.Updated_At] = "uploaded"
        sortMap[Ordering.Relevance] = "view"
    }

    // functions to implement TODO:
    // loadmangaFromBackup
    // updateWholeLibrary
    // restorBackup
    // backupManga
    // searchAllManga DONE
    // getChapters
    // updateInLibarry
    // downloadChapter
    // getNewUpdatedChapters

    // treate offSet as page number here
    // some stuff to handle gracefully
    // i.e some filters/sorts don't work and will probably crash app
    // content rating as well
    suspend fun searchAllManga(
        title: String,
        offSet: Int = 0,
        ordering: Pair<Ordering, Direction>,
        demo: List<Demographic>,
        tagsIncluded: List<Tag>,
        tagsExcluded: List<Tag>,
        rating: List<ContentRating>
    ): Pair<List<MangaInfo>, Int> {
        val tI = tagsIncluded.map { tagMap[it] }.toList().filterNotNull()
        val tE = tagsExcluded.map{tagMap[it]}.toList().filterNotNull()



        val demos: MutableList<Int> = demo.map {demoMap[it]}.toList().filterNotNull().toMutableList()
        if (demos.isEmpty()){
            demos.add(noDemo)
        }

        val pageNum = offSet + 1

        val contentRating = rating.firstOrNull()?.msg

        val result = apiService.searchAllManga(
            title,
            tI,
            tE,
            demo.mapNotNull { demoMap[it] },
            pageNum,
            contentRating,
            sortMap[ordering.first]
        )

        var mangaList: MutableList<MangaInfo> = mutableListOf()
        val reversed = demoMap.entries.associate { (k, v) -> v to k }
        for (manga in result){

            val hid = manga["hid"].toString()

            //thumbnail urls
            val md_covers = manga["md_covers"]
            var thumbnailUrl = ""
            if (md_covers is List<*>){
                val firstObj = md_covers[0]
                if (firstObj is Map<*,*>){
                    val picName = firstObj["b2key"].toString()
                    thumbnailUrl = "$basePictureUrl/$picName"
                }
            }

            val titl = manga["title"].toString()
            val type = "Manga"

            //alternate titles
            val md_titles = manga["md_titles"]
            val altTitles: MutableList<String> = mutableListOf()
            if (md_titles is List<*>){
                for (alt in md_titles){
                    if (alt is Map<*,*>){
                        val picName = alt["title"].toString()
                        altTitles.add(picName)
                    }
                }
            }

            val description = manga["desc"].toString()
            val demoInt = manga["demographic"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
            val demographic = reversed[demoInt]?.toString() ?: "safe"

            // tags
            val tagList: MutableList<Tag> = mutableListOf()
            val genreList = manga["genres"]
            if (genreList is List<*>){
                for (gen in genreList){
                    val id = gen.toString()
                    val intId = id.toDoubleOrNull()?.toInt() ?: 0
                    tagList.add(reversedGenreMap[intId] ?: continue)
                }
            }




            mangaList.add(
                MangaInfo(
                    id = hid,
                    type = "Manga",
                    title = titl,
                    alternateTitles = altTitles,
                    description = description,
                    state = mangaState.IN_PROGRESS,
                    contentRating = manga["contentRating"]?.toString() ?: "Shounen",
                    availableLanguages = listOf(),
                    coverArtUrl = thumbnailUrl,
                    coverArt = null,
                    offSet = offSet,
                    tagList = tagList.map { it.toString() }.toMutableList(),
                    demographic = demographic.toString(),
                    source = Sources.COMICK
                )
            )
        }

        return Pair(mangaList, mangaList.size)
    }

    suspend fun backUpLibrary(){
        val libraryShouldBe = library.map {element ->
            element.copy(chapterList = element.chapterList?.map { chapter ->
                chapter.copy(contents = if (chapter.contents?.isOnline == true) null else chapter.contents )
            } ?: listOf())
        }.toMutableList()

        val file = File(context.filesDir, "backup_comick.txt")
        Log.d("TAG", "backUpLibrary: ${gsonSerializer.toJson(BackUpInstance(libraryShouldBe, newUpdatedChapters, tagMap, demoMap, reversedGenreMap, sortMap, idSet))}")
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { fos ->
                // Create an OutputStreamWriter to write text data
                OutputStreamWriter(fos).use { writer ->
                    // Write the data to the file

                    writer.write(
                        gsonSerializer.toJson(BackUpInstance(libraryShouldBe, newUpdatedChapters, tagMap, demoMap, reversedGenreMap, sortMap, idSet))
                    )
                }
            }
        }
    }

    suspend fun getChapters(manga: MangaInfo): MangaInfo {
        val body = apiService.getChapterList(manga.id, 5000,1)

        val chapters: List<*> = if (body["chapters"] is List<*>) body["chapters"] as List<*> else return manga
        Log.d("TAG", "getChapters: found ${chapters.size} chapters")
        val foundChapters :List<Chapter> = chapters.map { ch ->
            if (ch is Map<*,*>){
                val title = ch["title"]?.toString() ?: ""
                val hid = ch["hid"].toString()
                val chNum = ch["chap"].toString().toDoubleOrNull() ?: -1.0
                val voNum = ch["vol"].toString().toDoubleOrNull() ?: -1.0
                val updated_at = ch["created_at"].toString()
                val date = SimpleDate(updated_at)
                val type = "Manga"
                val pageCount = 0.0

                val groupName = "Test Group"
                return@map Chapter(
                    name = title,
                    id = hid,
                    chapter = chNum,
                    volume = voNum,
                    type = type,
                    pageCount = pageCount,
                    group = groupName,
                    date = date
                )
            }
            null
        }.filterNotNull()

        val mergedChapters = foundChapters.map { ch ->
            manga.chapterList?.firstOrNull { it.id == ch.id  } ?: ch
        }

        return manga.copy(chapterList = mergedChapters)
    }

    suspend fun getChapterContents(ch: Chapter): Chapter {
        if (ch.contents != null && ch.contents is ChapterContents.Downloaded) {
            // sort the chapter image based on lexo compare
        }
        try {
            val body = apiService.getChapterPagesInfo(ch.id)
            if (body == null){
                Log.d("TAG", "getChapterContents: request went wrong")
                return ch
            }
            val imageList = body.map { imgObj ->
                val imgName = imgObj["b2key"].toString()
                "$basePictureUrl/$imgName"
            }

            val contents = ChapterContents.Online(imageList,false)
            Log.d("TAG", "getChapterContents: found chapter with ${imageList.size} pages")
            return ch.copy(contents = contents, pageCount = imageList.size.toDouble())
        } catch (e: Exception){
            Log.d("TAG", "getChapterContents: failed request $e")
            return ch
        }
    }

    suspend fun addToLibrary(manga: MangaInfo) {
        if (idSet.contains(manga.id)){
            Log.d("TAG", "already in library ")
            return
        }

        // mangadex also responsible for adding chapter list to the manga on add to library? it seems a bit unnecessary so wont add here.

        val d = downloadService.downloadCoverUrl(manga.id, manga.coverArtUrl)
        library.add(manga.copy(coverArtUrl = d))
        idSet.add(manga.id)

        // add call to backup library function
        Log.d("TAG", "addToLibrary: library is now $library ")
        Log.d("TAG", "addToLibrary: calling backupLibrary")
        backUpLibrary()

    }

    suspend fun removeFromLibrary(manga: MangaInfo?){
        library.removeIf { elm -> elm.id == manga?.id }

        idSet.remove(manga?.id)
        newUpdatedChapters.removeIf {
            it.second.mangaId == manga?.id
        }

        // call backup here as well
        backUpLibrary()
    }

    fun loadMangaFromBackup(context: Context){
        val file = File(context.filesDir, "backup_comick.txt")
        try {
            if (!file.exists()){
                Log.d("TAG", "loadMangaFromBackup: created new backup file for comick")
                file.writeText(gsonSerializer.toJson(BackUpInstance(library, newUpdatedChapters, tagMap, demoMap, reversedGenreMap, sortMap, idSet)))
                return
            }
            val jsonString = file.readText()

            val listType = object : TypeToken<BackUpInstance>() {}.type
            val r: BackUpInstance = gsonSerializer.fromJson(jsonString, listType)
            library = r.library
            idSet = r.idSet
            newUpdatedChapters = r.newUpdatedChapters
            tagMap = r.tagMap
            demoMap = r.demoMap
            reversedGenreMap = r.reversedGenreMap
            sortMap = r.sortMap
        } catch (e: Exception){
            Log.e("TAG", "Error loading manga from comick_backup.txt", e)
        }
    }

    suspend fun updateInLibrary(manga: MangaInfo) {
        library = library.map { elm ->
            if (manga.id == elm.id){
                val oldChapters = elm.chapterList?.toMutableList()
                manga.chapterList?.forEach { t ->
                    if (!oldChapters?.any{ e -> e.id == t.id }!!){
                        oldChapters.add(t)
                    } else {
                        val old = oldChapters.indexOfFirst { m -> m.id == t.id }
                        if (old != -1){
                            if (t.finished){
                                oldChapters[old] = t
                            }
                        }
                    }
                }
                manga.copy(chapterList = oldChapters ?: listOf())
            }else {
                elm
            }
        }.toMutableList()
        backUpLibrary()
    }

    fun getImageUri(mangaId: String, coverUrl: String): String {
        return downloadService.retrieveImage(mangaId,coverUrl).toString()
    }

    suspend fun downloadChapter(mangaId: String, chapterId: String) {
        var manga : MangaInfo = library.first {it.id == mangaId}
        var chList = manga.chapterList ?: return
        var ch = chList.first{it.id == chapterId}
        ch = getChapterContents(ch)
        val imageList = ch.contents?.imagePaths ?: return

        val uriList = imageList.map { downloadService.downloadContent(mangaId,chapterId, it)}
        val newContents = ChapterContents.Downloaded(uriList,false)
        ch = ch.copy(contents = newContents)

        chList = chList.map { if( it.id != ch.id) it else ch}
        manga = manga.copy(chapterList =  chList)

        library = library.map { if (it.id != mangaId) it else manga }.toMutableList()
        backUpLibrary()
    }

}






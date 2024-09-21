package com.example.poomagnet.ui.MangaSpecific

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowInsets
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.poomagnet.mangaDex.dexApiService.ChapterContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log


//use accompianist pager.
//create a list of functions first

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadingScreen(modifier: Modifier = Modifier, viewModel: MangaSpecificViewModel, pagerState: PagerState, context: Context, list: MutableState<List<@Composable () -> Unit>>){

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(pagerState, pagerState.pageCount) {
        snapshotFlow { pagerState.currentPageOffsetFraction}.collect { offset ->
            if (pagerState.currentPage == pagerState.pageCount-2 && offset > 0f) {
                Log.d("TAG", "ReadingScreen: is last page scroll")
                if (uiState.nextChapter !== null){
                    viewModel.loadNextChapter()
                    viewModel.setFlag(true)
                    delay(80)
                    pagerState.scrollToPage(0)
                    viewModel.setPage(1)
                }else {
                    viewModel.getNextChapter(context)
                    viewModel.loadNextChapter()
                    viewModel.setFlag(true)
                    delay(80)
                    pagerState.scrollToPage(0)
                    viewModel.setPage(1)
                }
            } else {
                if (pagerState.currentPage == pagerState.pageCount/2){
                    viewModel.getNextChapter(context)
                    Log.d("TAG", "ReadingScreen: getting next chapter")
                }
                Log.d("TAG", "ReadingScreen: changing to ${pagerState.pageCount}")
                viewModel.setPage(pagerState.currentPage+1)
            }
        }

    }


    HorizontalPager(pagerState, modifier.fillMaxSize()) { page ->
        list.value[page]()
    }
}

@Composable
fun ImageView(modifier: Modifier = Modifier, imageUrl: String, onClick: () -> Unit, context: Context){
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onClick() }, contentAlignment = Alignment.Center){
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Image",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun endScreen(modifier: Modifier = Modifier, currentChapter: String, nextChapter: String = ""){
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black), contentAlignment = Alignment.Center){
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally){
            Text(currentChapter)
        }
    }

}


@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ReadScreen(modifier: Modifier = Modifier, viewModel: MangaSpecificViewModel, returner: () -> Unit){
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val window = (view.context as Activity).window
    val insetsController = WindowCompat.getInsetsController(window,view)

    val list = remember { mutableStateOf(listOf<@Composable () -> Unit>()) }
    LaunchedEffect(uiState.nextFlag) {
        delay(80)
        Log.d("TAG", "ReadingScreen: flag hook triggered")
        if (uiState.nextFlag){
            val chapter = uiState.currentChapter
            if (chapter !== null){
                val contents = chapter.contents
                if (contents !== null){
                    Log.d("TAG", "ReadingScreen: loading pages")
                    when (contents){
                        is ChapterContents.Online -> {
                            val firstList: List<@Composable () -> Unit> = contents.imagePaths.map { elm ->
                                {ImageView(Modifier, imageUrl = elm.first, onClick = {viewModel.toggleReadBar() ; viewModel.toggleHomeBar()}, context)}
                            }
                            if (firstList.size == 0){
                                //inject warning for now but who cares.
                                Log.d("TAG", "chapter has no pages ")
                            }
                            list.value = firstList + { endScreen(Modifier, "End of Chapter")} + {Box(
                                Modifier
                                    .background(Color.Black)
                                    .fillMaxSize())}
                        }
                        is ChapterContents.Downloaded -> {
                            //deal with later
                        }
                    }

                }
            }
            viewModel.setFlag(false)
        }
    }

    val pagerState = rememberPagerState {list.value.size}


    BackHandler {
        viewModel.toggleHomeBar(true)
        viewModel.toggleReadBar(false)
        viewModel.markAsDone()
        viewModel.resetState()
        returner()
    }

    if (!view.isInEditMode){
        LaunchedEffect(uiState.homeBarVisible) {
            if (uiState.homeBarVisible){
                insetsController.apply { show(WindowInsetsCompat.Type.systemBars()) }
            } else {
                insetsController.apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
    }



    Scaffold(
        topBar = { MangaTopBar(Modifier, viewModel,returner)},
        bottomBar = { MangaBotBar(Modifier,viewModel, { pagerState.scrollToPage(0) })}
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()){
            ReadingScreen(Modifier.fillMaxSize(), viewModel, pagerState,context, list)
            if (uiState.currentPage <= uiState.currentChapter!!.pageCount.toInt()){
                Text("${uiState.currentPage}/${uiState.currentChapter?.pageCount?.toInt()}",
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(innerPadding))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaTopBar(modifier: Modifier = Modifier, viewModel: MangaSpecificViewModel, returner: () -> Unit){
    val uiState by viewModel.uiState.collectAsState()
    AnimatedVisibility(
        visible = uiState.readBarVisible,
        enter = fadeIn(animationSpec = tween(50)),
        exit = fadeOut(animationSpec = tween(50))
    ) {
        TopAppBar(
            title = {Text("Vol.${uiState.currentChapter?.volume} Ch. ${uiState.currentChapter?.chapter} ${uiState.currentChapter?.name}")},
            navigationIcon = {
                IconButton(onClick = { viewModel.toggleHomeBar(true)
                    viewModel.toggleReadBar(false)
                    returner() }) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = null
                    )
                }
            },
        )
    }
}

@Composable
fun MangaBotBar(modifier: Modifier = Modifier, viewModel: MangaSpecificViewModel, scrolltoZero: suspend ()->Unit){
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    AnimatedVisibility(
        visible = uiState.readBarVisible,
        enter = fadeIn(animationSpec = tween(50)),
        exit = fadeOut(animationSpec = tween(50))
    ) {
        BottomAppBar(actions = {
            Box(
                Modifier
                    .fillMaxSize()
                    .fillMaxHeight()) {
                IconButton(onClick = {
                    viewModel.viewModelScope.launch {
                        if (uiState.previousChapter == null){
                            Log.d("TAG", "MangaBotBar: loading previous chapter")
                            viewModel.getPreviousChapter(context)
                            viewModel.loadPreviousChapter()
                            viewModel.setFlag(true)
                            scrolltoZero()
                        } else {
                            viewModel.loadPreviousChapter()
                            viewModel.setFlag(true)
                            scrolltoZero()
                        }
                    }
                },Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBackIos,
                        contentDescription = null
                    )
                }

                IconButton(onClick = {
                    viewModel.viewModelScope.launch {
                        if (uiState.nextChapter == null){
                            viewModel.getNextChapter(context = context)
                            viewModel.loadNextChapter()
                            viewModel.setFlag(true)
                            scrolltoZero()
                        } else {
                            viewModel.loadNextChapter()
                            viewModel.setFlag(true)
                            scrolltoZero()
                        }
                    }
                },Modifier.align(Alignment.CenterEnd)) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowForwardIos,
                        contentDescription = null
                    )
                }
            }
        }, modifier = Modifier.height(110.dp))
    }
}

suspend fun preloadImage(context: Context, imageUrl: String) {
    val imageLoader = ImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(false) // If you need to avoid hardware bitmaps (optional)
        .build()

    withContext(Dispatchers.IO) {
        // Execute the request to preload the image
        val result = imageLoader.enqueue(request)
    }
}
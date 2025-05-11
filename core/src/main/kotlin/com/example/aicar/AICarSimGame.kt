package com.example.aicar

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.example.aicar.models.Car
import com.example.aicar.neat.Config
import com.example.aicar.neat.NEATAlgorithm
import com.example.aicar.neat.Population
import ktx.app.KtxGame
import ktx.app.KtxScreen
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.InputAdapter

class AICarSimGame : KtxGame<KtxScreen>() {
    override fun create() {
        Gdx.app.logLevel = Application.LOG_DEBUG
        addScreen(GameScreen())
        setScreen<GameScreen>()
    }
}

class GameScreen : KtxScreen {
    private lateinit var batch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont
    private lateinit var largeFont: BitmapFont
    private lateinit var mediumFont: BitmapFont
    private lateinit var smallFont: BitmapFont

    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: FitViewport

    private lateinit var neatAlgorithm: NEATAlgorithm
    private lateinit var population: Population

    private lateinit var aiCarSpriteSheet: Texture
    private lateinit var userCarSpriteSheet: Texture
    private lateinit var mapTexture: Texture
    private lateinit var mapPixmap: Pixmap
    private lateinit var mapThumbnailTexture: Texture

    private var backgroundMusic: Music? = null
    private var crashSfx: Sound? = null
    private var lapSfx: Sound? = null
    private var clickSfx: Sound? = null

    private var userCar: Car? = null
    private var playAgainstAi = true

    private var generationTime = 0f
    private val maxGenTimeSeconds = 45f

    private enum class State { HOME, RUNNING, GAMEOVER }
    private var currentState = State.HOME

    private var showOnlyLeader = false
    private var showRadars = true
    private var showPlacement = true
    private var showCheckpoints = true

    private lateinit var lapsInputRect: Rectangle
    private lateinit var lapsMinusButtonRect: Rectangle
    private lateinit var lapsPlusButtonRect: Rectangle
    private lateinit var playModeToggleRect: Rectangle
    private lateinit var mapSelectRect: Rectangle
    private lateinit var track2Rect: Rectangle
    private lateinit var track3Rect: Rectangle

    private lateinit var showLeaderToggleRect: Rectangle
    private lateinit var showRadarsToggleRect: Rectangle
    private lateinit var showPlacementToggleRect: Rectangle
    private lateinit var showCheckpointsToggleRect: Rectangle
    private lateinit var backToHomeButtonRect: Rectangle

    private lateinit var accelerateButtonRect: Rectangle
    private lateinit var brakeButtonRect: Rectangle
    private lateinit var turnLeftButtonRect: Rectangle
    private lateinit var turnRightButtonRect: Rectangle
    private lateinit var resetUserCarButtonRect: Rectangle

    private lateinit var backToHomeEndScreenRect: Rectangle
    private lateinit var restartSimButtonRect: Rectangle
    private var endScreenReason: String = ""
    private var endScreenStats: Map<String, Any?> = emptyMap()

    private val inputProcessor = GameInputProcessor()
    private var activeTouchPointers = mutableMapOf<Int, Rectangle>()

    private val fontScaleTitle = 4.5f
    private val fontScaleMedium = 2.8f
    private val fontScaleRegular = 2.0f
    private val fontScaleSmall = 1.6f

    private var statsPanelY: Float = 0f
    private val darkGreenActive = Color(0f, 0.5f, 0f, 1f) // Darker green for active buttons


    override fun show() {
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()

        font = BitmapFont().apply {
            data.setScale(fontScaleRegular)
            color = Color.WHITE
            regions.forEach { it.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        }
        largeFont = BitmapFont().apply {
            data.setScale(fontScaleTitle)
            color = Color.WHITE
            regions.forEach { it.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        }
        mediumFont = BitmapFont().apply {
            data.setScale(fontScaleMedium)
            color = Color.WHITE
            regions.forEach { it.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        }
        smallFont = BitmapFont().apply {
            data.setScale(fontScaleSmall)
            color = Color.WHITE
            regions.forEach { it.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        }

        camera = OrthographicCamera()
        viewport = FitViewport(Config.SCREEN_WIDTH_FLOAT, Config.SCREEN_HEIGHT_FLOAT, camera)
        camera.setToOrtho(false, viewport.worldWidth, viewport.worldHeight)

        try {
            mapTexture = Texture(Gdx.files.internal("img/maps/map1.png"))
            if (!mapTexture.textureData.isPrepared) mapTexture.textureData.prepare()
            mapPixmap = mapTexture.textureData.consumePixmap()

            val fullMapPixmap = Pixmap(Gdx.files.internal("img/maps/map1.png"))
            val thumbnailWidth = viewport.worldWidth * 0.25f
            val thumbnailHeight = thumbnailWidth * (200f / 320f)
            val thumbnailPixmap = Pixmap(thumbnailWidth.toInt(), thumbnailHeight.toInt(), fullMapPixmap.format)
            thumbnailPixmap.drawPixmap(fullMapPixmap, 0, 0, fullMapPixmap.width, fullMapPixmap.height, 0, 0, thumbnailWidth.toInt(), thumbnailHeight.toInt())
            mapThumbnailTexture = Texture(thumbnailPixmap)
            fullMapPixmap.dispose()
            thumbnailPixmap.dispose()

            aiCarSpriteSheet = Texture(Gdx.files.internal("img/car/BlueCar.png"))
            userCarSpriteSheet = Texture(Gdx.files.internal("img/car/RedCar.png"))
        } catch (e: Exception) {
            Gdx.app.error("GameScreenInit", "Error loading textures: ${e.message}", e)
        }

        neatAlgorithm = NEATAlgorithm()
        loadAudio()
        initializeUIBounds()
        Gdx.input.inputProcessor = inputProcessor
    }

    private fun loadAudio() {
        try {
            backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal("sfx/bg_music.mp3"))
            backgroundMusic?.let {
                it.isLooping = true
                it.volume = 0.1f
                it.play()
                Gdx.app.log("GameScreenAudio", "Background music loaded.")
            } ?: Gdx.app.error("GameScreenAudio", "Failed to load background music (asset is null).")
        } catch (e: Exception) {
            Gdx.app.error("GameScreenAudio", "Exception loading/playing background music: ${e.message}", e)
        }
        try { crashSfx = Gdx.audio.newSound(Gdx.files.internal("sfx/crash.mp3")) }
        catch (e: Exception) { Gdx.app.error("GameScreenAudio", "Error loading crash.mp3: ${e.message}", e) }
        try { lapSfx = Gdx.audio.newSound(Gdx.files.internal("sfx/lap.mp3")) }
        catch (e: Exception) { Gdx.app.error("GameScreenAudio", "Error loading lap.mp3: ${e.message}", e) }
        try { clickSfx = Gdx.audio.newSound(Gdx.files.internal("sfx/click.mp3")) }
        catch (e: Exception) { Gdx.app.error("GameScreenAudio", "Error loading click.mp3: ${e.message}", e) }
    }

    private fun initializeUIBounds() {
        // --- Home Screen ---
        val homeButtonBaseWidth = viewport.worldWidth * 0.35f
        val homeButtonHeight = viewport.worldHeight * 0.09f
        val homeThumbWidth = viewport.worldWidth * 0.28f
        val homeThumbHeight = homeThumbWidth * (200f/320f)
        val homeSpacing = viewport.worldWidth * 0.03f
        val sectionSpacing = viewport.worldHeight * 0.085f

        mapSelectRect = Rectangle( (viewport.worldWidth - homeThumbWidth * 3 - homeSpacing * 2) / 2f, viewport.worldHeight * 0.52f, homeThumbWidth, homeThumbHeight)
        track2Rect = Rectangle(mapSelectRect.x + homeThumbWidth + homeSpacing, mapSelectRect.y, homeThumbWidth, homeThumbHeight)
        track3Rect = Rectangle(track2Rect.x + homeThumbWidth + homeSpacing, mapSelectRect.y, homeThumbWidth, homeThumbHeight)

        val lapsInputY = mapSelectRect.y - homeButtonHeight - sectionSpacing
        val lapsLabelLayout = GlyphLayout(mediumFont, "Laps for AI to Win:")
        val lapsLabelWidth = lapsLabelLayout.width
        val lapsInputBoxWidth = viewport.worldWidth * 0.18f
        val lapsButtonSize = homeButtonHeight
        val totalLapsUIWidth = lapsLabelWidth + homeSpacing + lapsInputBoxWidth + homeSpacing + lapsButtonSize * 2
        val lapsInputStartX = (viewport.worldWidth - totalLapsUIWidth) / 2f
        lapsInputRect = Rectangle(lapsInputStartX + lapsLabelWidth + homeSpacing, lapsInputY, lapsInputBoxWidth, homeButtonHeight)
        lapsMinusButtonRect = Rectangle(lapsInputRect.x + lapsInputBoxWidth + homeSpacing, lapsInputY, lapsButtonSize, lapsButtonSize)
        lapsPlusButtonRect = Rectangle(lapsMinusButtonRect.x + lapsButtonSize + homeSpacing/2, lapsInputY, lapsButtonSize, lapsButtonSize)
        playModeToggleRect = Rectangle((viewport.worldWidth - homeButtonBaseWidth) / 2f, lapsInputY - homeButtonHeight - viewport.worldHeight * 0.05f, homeButtonBaseWidth, homeButtonHeight)

        // --- Running Screen ---
        // Control buttons (halved in size)
        val baseControlButtonSize = viewport.worldWidth * 0.11f
        val controlButtonSize = baseControlButtonSize * 0.5f // Halved size
        val controlButtonSpacing = viewport.worldWidth * 0.02f
        val controlAreaY = viewport.worldHeight * 0.025f

        turnLeftButtonRect = Rectangle(viewport.worldWidth * 0.04f, controlAreaY + controlButtonSize * 0.4f, controlButtonSize, controlButtonSize)
        turnRightButtonRect = Rectangle(turnLeftButtonRect.x + controlButtonSize + controlButtonSpacing, controlAreaY + controlButtonSize * 0.4f, controlButtonSize, controlButtonSize)
        accelerateButtonRect = Rectangle(viewport.worldWidth - controlButtonSize * 2 - controlButtonSpacing - viewport.worldWidth * 0.04f, controlAreaY + controlButtonSize + controlButtonSpacing, controlButtonSize, controlButtonSize)
        brakeButtonRect = Rectangle(viewport.worldWidth - controlButtonSize - viewport.worldWidth * 0.04f, controlAreaY + controlButtonSize + controlButtonSpacing, controlButtonSize, controlButtonSize)

        val resetBaseWidth = baseControlButtonSize * 1.5f
        val resetBaseHeight = baseControlButtonSize * 0.7f
        resetUserCarButtonRect = Rectangle((viewport.worldWidth - resetBaseWidth * 0.5f) / 2f, controlAreaY, resetBaseWidth * 0.5f, resetBaseHeight * 0.5f)


        // Stats Panel - Moved up, width reduced
        val statsPanelHeight = viewport.worldHeight * 0.24f
        val statsPanelWidth = viewport.worldWidth * 0.60f * 0.8f // Reduced width by 20%
        statsPanelY = (viewport.worldHeight * 0.65f) - (statsPanelHeight / 2f) // Centered around 65% height (moved up)
        val statsPanelX = (viewport.worldWidth - statsPanelWidth) / 2f

        // Toggle buttons - Width reduced
        val baseSimButtonWidth = viewport.worldWidth * 0.15f
        val simButtonWidth = baseSimButtonWidth * 0.6f // Reduced width by 20%
        val simButtonHeight = viewport.worldHeight * 0.040f
        val simButtonSpacing = viewport.worldWidth * 0.005f
        val simTogglePanelWidth = (simButtonWidth * 4) + (simButtonSpacing * 3)
        val simTogglePanelX = (viewport.worldWidth - simTogglePanelWidth) / 2f
        val simButtonY = statsPanelY - simButtonHeight - viewport.worldHeight * 0.035f

        showLeaderToggleRect = Rectangle(simTogglePanelX, simButtonY, simButtonWidth, simButtonHeight)
        showRadarsToggleRect = Rectangle(showLeaderToggleRect.x + simButtonWidth + simButtonSpacing, simButtonY, simButtonWidth, simButtonHeight)
        showCheckpointsToggleRect = Rectangle(showRadarsToggleRect.x + simButtonWidth + simButtonSpacing, simButtonY, simButtonWidth, simButtonHeight)
        showPlacementToggleRect = Rectangle(showCheckpointsToggleRect.x + simButtonWidth + simButtonSpacing, simButtonY, simButtonWidth, simButtonHeight)

        val exitButtonSize = viewport.worldWidth * 0.06f
        backToHomeButtonRect = Rectangle(viewport.worldWidth - exitButtonSize - viewport.worldWidth * 0.015f, viewport.worldHeight - exitButtonSize - viewport.worldHeight * 0.015f, exitButtonSize, exitButtonSize)

        // --- End Screen ---
        val endButtonWidth = viewport.worldWidth * 0.4f
        val endButtonHeight = viewport.worldHeight * 0.09f
        val endButtonSpacing = viewport.worldWidth * 0.05f
        val totalEndButtonWidth = 2 * endButtonWidth + endButtonSpacing
        val endButtonStartX = (viewport.worldWidth - totalEndButtonWidth) / 2f
        val endButtonY = viewport.worldHeight * 0.30f
        backToHomeEndScreenRect = Rectangle(endButtonStartX, endButtonY, endButtonWidth, endButtonHeight)
        restartSimButtonRect = Rectangle(endButtonStartX + endButtonWidth + endButtonSpacing, endButtonY, endButtonWidth, endButtonHeight)
    }

    private fun initializeAndSpawnUserCar() {
        if (playAgainstAi) {
            userCar = Car(
                Config.LIBGDX_START_X, Config.LIBGDX_START_Y,
                userCarSpriteSheet, null, mapPixmap, Config.SCREEN_HEIGHT_FLOAT
            )
        } else {
            userCar = null
        }
    }

    private fun startNewGeneration() {
        population.createCarsFromGenomes(mapPixmap, Config.SCREEN_HEIGHT_FLOAT, aiCarSpriteSheet)
        if (playAgainstAi) {
            userCar?.reset(Config.LIBGDX_START_X, Config.LIBGDX_START_Y) ?: initializeAndSpawnUserCar()
        } else {
            userCar = null
        }
        generationTime = 0f
        Gdx.app.log("GameScreen", "Generation ${population.generation} started. AI Cars: ${population.cars.size}")
    }

    private fun startRun() {
        neatAlgorithm.resetGlobalState()
        population = neatAlgorithm.initializePopulation(mapPixmap, Config.SCREEN_HEIGHT_FLOAT, aiCarSpriteSheet)
        startNewGeneration()
        currentState = State.RUNNING
        generationTime = 0f
        endScreenReason = ""; endScreenStats = emptyMap()
    }

    override fun render(delta: Float) {
        viewport.apply()
        camera.update()
        val bg = when (currentState) {
            State.HOME -> Color(Config.HOME_SCREEN_BG_COLOR)
            State.GAMEOVER -> Color(Config.END_SCREEN_BG_COLOR)
            State.RUNNING -> Color(0.05f, 0.05f, 0.05f, 1f)
        }
        Gdx.gl.glClearColor(bg.r, bg.g, bg.b, bg.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        when (currentState) {
            State.HOME -> renderHomeScreen()
            State.RUNNING -> renderRunning(delta)
            State.GAMEOVER -> renderGameOverScreen()
        }
    }

    private fun renderHomeScreen() {
        batch.projectionMatrix = camera.combined
        shapeRenderer.projectionMatrix = camera.combined
        batch.begin()
        // Updated title text
        val titleText = "AI Car Evolution Sim (pick map to start)"
        val titleLayout = GlyphLayout(largeFont, titleText)
        largeFont.draw(batch, titleLayout, (viewport.worldWidth - titleLayout.width) / 2f, viewport.worldHeight * 0.90f)
        // Subtitle removed
        batch.end()

        fun drawMapThumbnail(rect: Rectangle, texture: Texture?, label: String, available: Boolean) {
            val mousePos = viewport.unproject(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
            val isPressed = activeTouchPointers.values.any { it === rect } && available
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = when {
                isPressed -> Color.LIME; available && rect.contains(mousePos) -> Color.ROYAL
                available -> Color.NAVY; else -> Color.DARK_GRAY
            }
            shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height); shapeRenderer.end()
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line); shapeRenderer.color = Color.LIGHT_GRAY
            shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height); shapeRenderer.end()
            batch.begin()
            if (texture != null && available) batch.draw(texture, rect.x + 5, rect.y + 5, rect.width - 10, rect.height - 10)
            else if (!available) {
                val soonLayout = GlyphLayout(mediumFont, "Soon!"); mediumFont.draw(batch, soonLayout, rect.x + (rect.width - soonLayout.width) / 2f, rect.y + (rect.height + soonLayout.height) / 2f)
            }
            val labelLayout = GlyphLayout(font, label); font.draw(batch, labelLayout, rect.x + (rect.width - labelLayout.width) / 2f, rect.y - labelLayout.height - 20f)
            batch.end()
        }
        drawMapThumbnail(mapSelectRect, mapThumbnailTexture, "Default Track", true)
        drawMapThumbnail(track2Rect, null, "Track 2", false)
        drawMapThumbnail(track3Rect, null, "Track 3", false)

        batch.begin()
        val lapsLabelLayout = GlyphLayout(mediumFont, "Laps for AI to Win:")
        mediumFont.draw(batch, lapsLabelLayout, lapsInputRect.x - lapsLabelLayout.width - viewport.worldWidth * 0.02f, lapsInputRect.y + (lapsInputRect.height + lapsLabelLayout.height) / 2f)
        batch.end()
        drawButton(lapsInputRect, lapsInputText.toString(), false, mediumFont, false)
        drawButton(lapsMinusButtonRect, "-", false, largeFont)
        drawButton(lapsPlusButtonRect, "+", false, largeFont)
        val modeText = if (playAgainstAi) "Mode: User vs AI" else "Mode: AI Only"
        drawButton(playModeToggleRect, modeText, false, mediumFont)
        batch.begin()
        val instrLayout = GlyphLayout(font, "Tap map to start. ESC for Back/Exit.")
        font.draw(batch, instrLayout, (viewport.worldWidth - instrLayout.width) / 2f, viewport.worldHeight * 0.12f)
        batch.end()
    }

    private var lapsInputText: Int = 1

    private fun renderRunning(delta: Float) {
        generationTime += delta
        population.cars.forEach { if (it.brain != null && it.isAlive) it.update(delta) }
        userCar?.let { if (it.isAlive) it.update(delta) }

        val targetLaps = lapsInputText
        val aiWin = population.cars.any { it.brain != null && it.laps >= targetLaps }
        val userWin = userCar?.let { it.laps >= targetLaps } == true

        if (userWin) {
            currentState = State.GAMEOVER; endScreenReason = "YOU WIN!"; clickSfx?.play();
            endScreenStats = mapOf("Winner" to "User", "Laps" to (userCar?.laps ?: 0))
            return
        }
        if (aiWin) {
            currentState = State.GAMEOVER; endScreenReason = if(playAgainstAi) "AI DEFEATS YOU!" else "AI COMPLETES RUN!" ; clickSfx?.play()
            val bestAI = population.cars.filter { it.brain != null }.maxByOrNull { it.fitness }
            endScreenStats = mapOf("Winner" to "AI", "Laps" to targetLaps, "Best AI Fitness" to String.format("%.2f", bestAI?.fitness ?: 0f))
            return
        }
        val allAIDead = population.cars.none { it.brain != null && it.isAlive }
        if (allAIDead || generationTime >= maxGenTimeSeconds) {
            if (population.generation >= Config.MAX_NEAT_GENERATIONS) {
                currentState = State.GAMEOVER; endScreenReason = "MAX GENERATIONS REACHED"; clickSfx?.play()
                val bestAI = population.genomes.maxOfOrNull { it.fitness }
                endScreenStats = mapOf("Reason" to "Max Gens", "Last Gen" to population.generation, "Best AI Fitness" to String.format("%.2f", bestAI ?: 0f))
                return
            }
            Gdx.app.log("GameScreen", "Generation ${population.generation} ended. Evolving...")
            neatAlgorithm.evolvePopulation(population)
            startNewGeneration()
            return
        }

        batch.projectionMatrix = camera.combined
        shapeRenderer.projectionMatrix = camera.combined
        batch.begin(); batch.draw(mapTexture, 0f, 0f, viewport.worldWidth, viewport.worldHeight); batch.end()
        batch.begin()
        val carsToRender = if (showOnlyLeader) listOfNotNull(population.cars.filter { it.brain != null && it.isAlive }.maxByOrNull { it.fitness })
        else population.cars.filter { it.brain != null && it.isAlive }
        carsToRender.forEach { it.renderSprites(batch) }
        userCar?.let { if (it.isAlive) it.renderSprites(batch) }
        batch.end()

        val aliveCars = (userCar?.takeIf { it.isAlive }?.let(::listOf) ?: emptyList()) + population.cars.filter { it.brain != null && it.isAlive }

        val rankedCars = aliveCars.sortedWith(compareByDescending<Car> { it.laps }.thenByDescending { it.nextCpIndex }.thenByDescending { it.getProgressMetric().third })
        rankedCars.forEachIndexed { idx, car: Car -> car.currentRank = idx + 1 }

        if (showPlacement) {
            batch.begin()
            val carsToDrawRank = if (showOnlyLeader) rankedCars.take(1) else rankedCars
            carsToDrawRank.forEach { it.drawRank(batch, smallFont) }
            batch.end()
        }
        if (showRadars) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            Gdx.gl20.glLineWidth(2f)
            val carsToDrawRadars = if (showOnlyLeader) rankedCars.take(1) else population.cars
            carsToDrawRadars.forEach { if (it.brain != null && it.isAlive) it.drawRadars(shapeRenderer) }
            shapeRenderer.end()
            Gdx.gl20.glLineWidth(1f)
        }
        if (showCheckpoints) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            val carForCheckpoints = if (showOnlyLeader) rankedCars.firstOrNull() else userCar?.takeIf {it.isAlive} ?: rankedCars.firstOrNull()
            carForCheckpoints?.let { car ->
                Car.CHECKPOINT_RECTS.forEachIndexed { idx, rect ->
                    shapeRenderer.color = if (idx == car.nextCpIndex) Config.NEXT_CHECKPOINT_COLOR else Config.CHECKPOINT_COLOR
                    shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
                }
            }
            shapeRenderer.end()
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

        val panelW = viewport.worldWidth * 0.48f // Adjusted width
        val panelH = viewport.worldHeight * 0.24f
        val panelX = (viewport.worldWidth - panelW) / 2f
        // statsPanelY is now a class member, calculated in initializeUIBounds and used here

        Gdx.gl.glEnable(GL20.GL_BLEND); shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color.set(0.05f, 0.05f, 0.15f, 0.85f)
        shapeRenderer.rect(panelX, statsPanelY, panelW, panelH); shapeRenderer.end(); Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.begin()
        val statFont = font
        statFont.data.setScale(fontScaleRegular)

        var ty = statsPanelY + panelH - viewport.worldHeight * 0.025f
        val lh = statFont.lineHeight * 1.2f

        statFont.draw(batch, "Generation: ${population.generation}/${Config.MAX_NEAT_GENERATIONS}", panelX + 20f, ty); ty -= lh
        val aliveCount = population.cars.count { it.brain != null && it.isAlive }
        statFont.draw(batch, "AI Alive: $aliveCount/${Config.POPULATION_SIZE}", panelX + 20f, ty); ty -= lh
        userCar?.let { statFont.draw(batch, "User Laps: ${it.laps} | Alive: ${it.isAlive}", panelX + 20f, ty); ty -= lh }
        statFont.draw(batch, "Time: ${"%.1f".format(generationTime)}s / ${maxGenTimeSeconds.toInt()}s", panelX + 20f, ty); ty -= lh
        val bestFit = population.genomes.maxOfOrNull { it.fitness } ?: 0f
        statFont.draw(batch, "Max AI Fitness: ${"%.2f".format(bestFit)}", panelX + 20f, ty)

        val leaderboardHeader = "Leaderboard (Top ${minOf(rankedCars.size, 3)}):"
        val lbHeaderLayout = GlyphLayout(statFont, leaderboardHeader)
        val availablePanelWidthForStats = panelW - 40f
        val mainStatsApproxWidth = availablePanelWidthForStats * 0.55f
        val leaderboardApproxWidth = availablePanelWidthForStats * 0.40f
        val lbX: Float
        var lbY: Float

        if (mainStatsApproxWidth + leaderboardApproxWidth < availablePanelWidthForStats && rankedCars.isNotEmpty()) {
            lbX = panelX + 20f + mainStatsApproxWidth + 15f
            lbY = statsPanelY + panelH - viewport.worldHeight * 0.025f
        } else {
            lbX = panelX + 20f
            lbY = ty - lh * 0.5f
        }

        if (rankedCars.isNotEmpty()) {
            statFont.draw(batch, leaderboardHeader, lbX, lbY); lbY -= lh
            rankedCars.take(3).forEach { car ->
                val label = car.brain?.let { "AI ${it.genome.hashCode() % 100}" } ?: "User"
                val entry = "L${car.laps} CP${car.nextCpIndex} $label"
                val entryLayout = GlyphLayout(statFont, entry)
                if (lbX > panelX + mainStatsApproxWidth && lbX + entryLayout.width > panelX + panelW - 10f) {
                    statFont.draw(batch, entry, lbX, lbY, panelX + panelW - lbX - 10f, Align.left, true)
                } else {
                    statFont.draw(batch, entry, lbX, lbY)
                }
                lbY -= lh
            }
        }
        batch.end()

        // Use 'font' for toggle buttons (fontScaleRegular = 2.0f), which is ~25% > smallFont (1.6f)
        val toggleButtonFont = font
        drawButton(showLeaderToggleRect, if (showOnlyLeader) "All Cars" else "Leader", showOnlyLeader, toggleButtonFont, true, activeColor = darkGreenActive)
        drawButton(showRadarsToggleRect, if (showRadars) "No Radar" else "Radar", showRadars, toggleButtonFont, true, activeColor = darkGreenActive)
        drawButton(showCheckpointsToggleRect, if (showCheckpoints) "No CPs" else "CPs", showCheckpoints, toggleButtonFont, true, activeColor = darkGreenActive)
        drawButton(showPlacementToggleRect, if (showPlacement) "No Rank" else "Rank", showPlacement, toggleButtonFont, true, activeColor = darkGreenActive)

        if (playAgainstAi && userCar != null) {
            val controlButtonFont = smallFont // Keep smallFont for very small control buttons
            drawButton(accelerateButtonRect, "GAS", userCar!!.wantsToAccelerate, controlButtonFont, true, darkGreenActive, Color(0f,0.30f,0f,1f)) // Darker base for gas
            drawButton(brakeButtonRect, "BRAKE", userCar!!.wantsToBrake, controlButtonFont, true, Color.RED, Color.MAROON)
            drawButton(turnLeftButtonRect, "LEFT", userCar!!.wantsToTurnLeft, controlButtonFont, true, Color.valueOf("FFC107"), Color.valueOf("B8860B")) // Amber/DarkGoldenrod
            drawButton(turnRightButtonRect, "RIGHT", userCar!!.wantsToTurnRight, controlButtonFont, true, Color.valueOf("FFC107"), Color.valueOf("B8860B"))
            drawButton(resetUserCarButtonRect, "RESET", false, controlButtonFont, true, Color.ORANGE, Color.BROWN)
        }
        drawButton(backToHomeButtonRect, "X", false, mediumFont, true, Color.FIREBRICK, Color.MAROON)
    }

    private fun renderGameOverScreen() {
        batch.projectionMatrix = camera.combined
        shapeRenderer.projectionMatrix = camera.combined
        batch.begin()
        val titleLayout = GlyphLayout(largeFont, endScreenReason)
        largeFont.draw(batch, titleLayout, (viewport.worldWidth - titleLayout.width) / 2f, viewport.worldHeight * 0.80f)
        var ty = viewport.worldHeight * 0.65f
        val lh = mediumFont.lineHeight * 1.3f
        endScreenStats.forEach { (key, value) ->
            val statText = "$key: $value"
            val statLayout = GlyphLayout(mediumFont, statText)
            mediumFont.draw(batch, statLayout, (viewport.worldWidth - statLayout.width) / 2f, ty)
            ty -= lh
        }
        batch.end()
        drawButton(backToHomeEndScreenRect, "Back to Home", false, mediumFont, true, activeColor = darkGreenActive)
        drawButton(restartSimButtonRect, "Restart Simulation", false, mediumFont, true, activeColor = darkGreenActive)
    }

    private fun drawButton(rect: Rectangle, text: String, isActive: Boolean, fontToUse: BitmapFont, isClickable: Boolean = true, activeColor: Color = darkGreenActive, baseColor: Color = Color.NAVY) {
        val mousePos = viewport.unproject(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
        val isPressed = activeTouchPointers.values.any { it === rect }
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = when {
            isPressed -> activeColor.cpy().lerp(Color.BLACK, 0.4f)
            isActive && isClickable -> activeColor
            // rect.contains(mousePos) && isClickable && Gdx.input.isTouched() -> baseColor.cpy().lerp(Color.LIGHT_GRAY, 0.4f) // Hover effect for touch can be tricky
            else -> baseColor
        }
        shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height); shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line); shapeRenderer.color = Color.LIGHT_GRAY
        shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height); shapeRenderer.end()
        batch.begin()
        val currentFontScaleX = fontToUse.data.scaleX; val currentFontScaleY = fontToUse.data.scaleY
        var layout = GlyphLayout(fontToUse, text) // Initial layout
        // Auto-scale font if text is wider than button (more aggressive)
        val targetWidth = rect.width * 0.9f // Allow some padding
        if (layout.width > targetWidth) {
            val requiredScale = currentFontScaleX * (targetWidth / layout.width)
            fontToUse.data.setScale(requiredScale)
            layout = GlyphLayout(fontToUse, text) // Re-layout with new scale
        }
        fontToUse.draw(batch, layout, rect.x + (rect.width - layout.width) / 2f, rect.y + (rect.height + layout.height) / 2f)
        fontToUse.data.setScale(currentFontScaleX, currentFontScaleY) // Restore original scale
        batch.end()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        camera.position.set(viewport.worldWidth / 2, viewport.worldHeight / 2, 0f)
        initializeUIBounds()
        Gdx.app.log("GameScreen", "Resized to $width x $height. Viewport: ${viewport.worldWidth} x ${viewport.worldHeight}")
    }

    override fun dispose() {
        batch.dispose(); shapeRenderer.dispose()
        font.dispose(); largeFont.dispose(); mediumFont.dispose(); smallFont.dispose()
        aiCarSpriteSheet.dispose(); userCarSpriteSheet.dispose()
        mapTexture.dispose(); mapThumbnailTexture.dispose(); mapPixmap.dispose()
        backgroundMusic?.dispose(); Gdx.app.log("GameScreenAudio", "Background music disposed.")
        crashSfx?.dispose(); lapSfx?.dispose(); clickSfx?.dispose()
        if (Gdx.input.inputProcessor == inputProcessor) Gdx.input.inputProcessor = null
    }

    inner class GameInputProcessor : InputAdapter() {
        private fun processTap(worldX: Float, worldY: Float, pointer: Int): Boolean {
            fun check(buttonRect: Rectangle, onHit: () -> Unit): Boolean {
                if (buttonRect.contains(worldX, worldY)) {
                    activeTouchPointers[pointer] = buttonRect
                    onHit()
                    clickSfx?.play(0.7f)
                    return true
                }
                return false
            }

            when (currentState) {
                State.HOME -> {
                    if (check(mapSelectRect) { if (lapsInputText > 0) startRun() }) return true
                    if (check(lapsMinusButtonRect) { lapsInputText = maxOf(1, lapsInputText - 1) }) return true
                    if (check(lapsPlusButtonRect) { lapsInputText++ }) return true
                    if (check(playModeToggleRect) { playAgainstAi = !playAgainstAi }) return true
                }
                State.RUNNING -> {
                    if (check(backToHomeButtonRect) { currentState = State.HOME }) return true
                    if (check(showLeaderToggleRect) { showOnlyLeader = !showOnlyLeader }) return true
                    if (check(showRadarsToggleRect) { showRadars = !showRadars }) return true
                    if (check(showCheckpointsToggleRect) { showCheckpoints = !showCheckpoints }) return true
                    if (check(showPlacementToggleRect) { showPlacement = !showPlacement }) return true
                    if (check(resetUserCarButtonRect) { userCar?.reset(Config.LIBGDX_START_X, Config.LIBGDX_START_Y)}) return true
                }
                State.GAMEOVER -> {
                    if (check(backToHomeEndScreenRect) { currentState = State.HOME }) return true
                    if (check(restartSimButtonRect) { startRun() }) return true
                }
            }
            return false
        }

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val worldPos = viewport.unproject(Vector2(screenX.toFloat(), screenY.toFloat()))

            if (currentState == State.RUNNING && userCar != null) {
                if (accelerateButtonRect.contains(worldPos)) { userCar!!.wantsToAccelerate = true; activeTouchPointers[pointer] = accelerateButtonRect; return true }
                if (brakeButtonRect.contains(worldPos)) { userCar!!.wantsToBrake = true; activeTouchPointers[pointer] = brakeButtonRect; return true }
                if (turnLeftButtonRect.contains(worldPos)) { userCar!!.wantsToTurnLeft = true; activeTouchPointers[pointer] = turnLeftButtonRect; return true }
                if (turnRightButtonRect.contains(worldPos)) { userCar!!.wantsToTurnRight = true; activeTouchPointers[pointer] = turnRightButtonRect; return true }
            }
            return processTap(worldPos.x, worldPos.y, pointer)
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val releasedButtonRect = activeTouchPointers.remove(pointer)

            if (currentState == State.RUNNING && userCar != null && releasedButtonRect != null) {
                if (releasedButtonRect === accelerateButtonRect) userCar!!.wantsToAccelerate = false
                if (releasedButtonRect === brakeButtonRect) userCar!!.wantsToBrake = false
                if (releasedButtonRect === turnLeftButtonRect) userCar!!.wantsToTurnLeft = false
                if (releasedButtonRect === turnRightButtonRect) userCar!!.wantsToTurnRight = false
            }
            return releasedButtonRect != null
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (currentState == State.RUNNING && userCar != null) {
                val currentHeldButton = activeTouchPointers[pointer] ?: return false // Not dragging a known button
                val worldPos = viewport.unproject(Vector2(screenX.toFloat(), screenY.toFloat()))

                var stillOnAControl = false
                // Check if still on the original button or slid onto another
                if (accelerateButtonRect.contains(worldPos)) {
                    if(activeTouchPointers[pointer] != accelerateButtonRect) { userCar!!.wantsToAccelerate = true; activeTouchPointers[pointer] = accelerateButtonRect } // Slid onto
                    else { userCar!!.wantsToAccelerate = true } // Still on
                    stillOnAControl = true
                } else if (activeTouchPointers[pointer] == accelerateButtonRect) userCar!!.wantsToAccelerate = false // Slid off

                if (brakeButtonRect.contains(worldPos)) {
                    if(activeTouchPointers[pointer] != brakeButtonRect) { userCar!!.wantsToBrake = true; activeTouchPointers[pointer] = brakeButtonRect }
                    else { userCar!!.wantsToBrake = true }
                    stillOnAControl = true
                } else if (activeTouchPointers[pointer] == brakeButtonRect) userCar!!.wantsToBrake = false

                if (turnLeftButtonRect.contains(worldPos)) {
                    if(activeTouchPointers[pointer] != turnLeftButtonRect) { userCar!!.wantsToTurnLeft = true; activeTouchPointers[pointer] = turnLeftButtonRect }
                    else { userCar!!.wantsToTurnLeft = true }
                    stillOnAControl = true
                } else if (activeTouchPointers[pointer] == turnLeftButtonRect) userCar!!.wantsToTurnLeft = false

                if (turnRightButtonRect.contains(worldPos)) {
                    if(activeTouchPointers[pointer] != turnRightButtonRect) { userCar!!.wantsToTurnRight = true; activeTouchPointers[pointer] = turnRightButtonRect }
                    else { userCar!!.wantsToTurnRight = true }
                    stillOnAControl = true
                } else if (activeTouchPointers[pointer] == turnRightButtonRect) userCar!!.wantsToTurnRight = false

                if(!stillOnAControl && activeTouchPointers[pointer] != null){
                    // If the pointer was on a control button but is no longer on *any* control button it was previously on
                    // (this logic might need refinement if multiple control buttons can be pressed by different fingers)
                    // For now, if it's not on the *current* held button, we assume it slid off that specific control.
                    // The individual "slid off" checks above should handle this.
                    // This is a fallback if a pointer is active but not on its original button.
                    // activeTouchPointers.remove(pointer) // This might be too aggressive if sliding between buttons.
                }
            }
            return activeTouchPointers.containsKey(pointer)
        }

        override fun keyDown(keycode: Int): Boolean {
            if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                if (currentState == State.RUNNING || currentState == State.GAMEOVER) {
                    currentState = State.HOME
                    userCar?.apply { wantsToAccelerate=false; wantsToBrake=false; wantsToTurnLeft=false; wantsToTurnRight=false;}
                    return true
                } else if (currentState == State.HOME) {
                    Gdx.app.exit()
                    return true
                }
            }
            if (currentState == State.RUNNING && userCar != null) {
                when(keycode) {
                    com.badlogic.gdx.Input.Keys.UP -> { userCar?.wantsToAccelerate = true; return true }
                    com.badlogic.gdx.Input.Keys.DOWN -> { userCar?.wantsToBrake = true; return true }
                    com.badlogic.gdx.Input.Keys.LEFT -> { userCar?.wantsToTurnLeft = true; return true }
                    com.badlogic.gdx.Input.Keys.RIGHT -> { userCar?.wantsToTurnRight = true; return true }
                    com.badlogic.gdx.Input.Keys.SPACE -> { userCar?.reset(Config.LIBGDX_START_X, Config.LIBGDX_START_Y); return true}
                    else -> return false
                }
            }
            return false
        }

        override fun keyUp(keycode: Int): Boolean {
            if (currentState == State.RUNNING && userCar != null) {
                when(keycode) {
                    com.badlogic.gdx.Input.Keys.UP -> { userCar?.wantsToAccelerate = false; return true }
                    com.badlogic.gdx.Input.Keys.DOWN -> { userCar?.wantsToBrake = false; return true }
                    com.badlogic.gdx.Input.Keys.LEFT -> { userCar?.wantsToTurnLeft = false; return true }
                    com.badlogic.gdx.Input.Keys.RIGHT -> { userCar?.wantsToTurnRight = false; return true }
                    else -> return false
                }
            }
            return false
        }
    }
}

package com.lhstack.jtools

import ai.grazie.utils.mpp.UUID
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.lhstack.tools.plugins.Helper
import com.lhstack.tools.plugins.IPlugin
import java.awt.*
import java.awt.event.*
import javax.swing.*

class PluginImpl : IPlugin {

    companion object {
        val CACHE = mutableMapOf<String, JComponent>()
        val DISPOSABLES = mutableMapOf<String, Disposable>()
    }

    override fun pluginIcon(): Icon = Helper.findIcon("pane.svg", PluginImpl::class.java)

    override fun pluginTabIcon(): Icon = Helper.findIcon("tab.svg", PluginImpl::class.java)

    override fun createPanel(project: Project): JComponent = JPanel().also { panel ->
        panel.layout = BorderLayout()
        val identity = UUID.random().text
        val parentDisposable = Disposer.newDisposable()

        val sokobanGame = SokobanGamePanel()
        panel.add(sokobanGame, BorderLayout.CENTER)

        CACHE[identity] = panel
        DISPOSABLES[identity] = parentDisposable
        panel.name = identity
    }

    override fun closePanel(project: Project, pluginPanel: JComponent) {
        CACHE.remove(pluginPanel.name)
        DISPOSABLES.remove(pluginPanel.name)?.let { Disposer.dispose(it) }
    }

    override fun tabPanelActions(project: Project?): MutableList<AnAction> {
        return super.tabPanelActions(project)
    }

    override fun supportMultiOpens(): Boolean = true

    override fun pluginName(): String = "推箱子游戏"

    override fun pluginDesc(): String = "经典的推箱子益智游戏"

    override fun pluginVersion(): String = "1.0.0"
}

class SokobanGamePanel : JPanel(), KeyListener {

    private var gameBoard: Array<CharArray> = arrayOf()
    private var originalBoard: Array<CharArray> = arrayOf() // 存储原始地图用于目标位置判断
    private var playerRow = 0
    private var playerCol = 0
    private var level = 1
    private var moves = 0
    private var isGameWon = false
    private var isInfiniteMode = false

    private val statusLabel = JLabel("关卡: $level | 步数: $moves")
    private val gameCanvas = GameCanvas()

    init {
        layout = BorderLayout()
        isFocusable = true
        addKeyListener(this)
        
        // 添加鼠标点击事件来获取焦点
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                requestFocusInWindow()
            }
        })

        val controlPanel = JPanel(FlowLayout())
        val restartButton = JButton("重新开始")
        val nextLevelButton = JButton("下一关")
        val randomLevelButton = JButton("随机关卡")
        val infiniteModeButton = JButton("无限火力")

        restartButton.addActionListener {
            restartLevel()
            requestFocusInWindow()
        }

        nextLevelButton.addActionListener {
            nextLevel()
            requestFocusInWindow()
        }

        randomLevelButton.addActionListener {
            randomLevel()
            requestFocusInWindow()
        }

        infiniteModeButton.addActionListener {
            toggleInfiniteMode()
            requestFocusInWindow()
        }

        controlPanel.add(restartButton)
        controlPanel.add(nextLevelButton)
        controlPanel.add(randomLevelButton)
        controlPanel.add(infiniteModeButton)
        controlPanel.add(statusLabel)

        add(controlPanel, BorderLayout.NORTH)
        add(gameCanvas, BorderLayout.CENTER)

        initLevel(level)
        
        // 使用SwingUtilities.invokeLater确保组件完全初始化后再请求焦点
        SwingUtilities.invokeLater {
            requestFocusInWindow()
        }
    }

    private fun initLevel(levelNum: Int) {
        isGameWon = false
        moves = 0
        gameBoard = getLevelData(levelNum)
        originalBoard = getLevelData(levelNum) // 保存原始地图的副本
        findPlayerPosition()
        updateStatus()
        gameCanvas.repaint()
    }

    private fun getLevelData(levelNum: Int): Array<CharArray> {
        val levels = arrayOf(
            // 关卡1 - 简单入门
            arrayOf(
                "########".toCharArray(),
                "#......#".toCharArray(),
                "#...$..#".toCharArray(),
                "#..@...#".toCharArray(),
                "#......#".toCharArray(),
                "#...O..#".toCharArray(),
                "#......#".toCharArray(),
                "########".toCharArray()
            ),
            // 关卡2 - 双箱子
            arrayOf(
                "##########".toCharArray(),
                "#........#".toCharArray(),
                "#..$..O..#".toCharArray(),
                "#........#".toCharArray(),
                "#..@.....#".toCharArray(),
                "#........#".toCharArray(),
                "#....O.$.#".toCharArray(),
                "#........#".toCharArray(),
                "##########".toCharArray()
            ),
            // 关卡3 - 三箱子
            arrayOf(
                "############".toCharArray(),
                "#..........#".toCharArray(),
                "#..$..$$...#".toCharArray(),
                "#..........#".toCharArray(),
                "#....@.....#".toCharArray(),
                "#..........#".toCharArray(),
                "#...OO.O...#".toCharArray(),
                "#..........#".toCharArray(),
                "############".toCharArray()
            ),
            // 关卡4 - 走廊挑战
            arrayOf(
                "##############".toCharArray(),
                "#............#".toCharArray(),
                "#.O.$..$.O...#".toCharArray(),
                "#............#".toCharArray(),
                "#......@.....#".toCharArray(),
                "#............#".toCharArray(),
                "##############".toCharArray()
            ),
            // 关卡5 - 十字形
            arrayOf(
                "######".toCharArray(),
                "#....#".toCharArray(),
                "#.O$.#".toCharArray(),
                "##.###".toCharArray(),
                "#.@..#".toCharArray(),
                "#....#".toCharArray(),
                "######".toCharArray()
            ),
            // 关卡6 - L形迷宫
            arrayOf(
                "##########".toCharArray(),
                "#........#".toCharArray(),
                "#.$......#".toCharArray(),
                "#........#".toCharArray(),
                "####.#####".toCharArray(),
                "#..@.....#".toCharArray(),
                "#........#".toCharArray(),
                "#.......O#".toCharArray(),
                "##########".toCharArray()
            ),
            // 关卡7 - 方形挑战
            arrayOf(
                "############".toCharArray(),
                "#..........#".toCharArray(),
                "#.$........#".toCharArray(),
                "#..........#".toCharArray(),
                "#....##....#".toCharArray(),
                "#....#O....#".toCharArray(),
                "#....##....#".toCharArray(),
                "#..........#".toCharArray(),
                "#.......@..#".toCharArray(),
                "#..........#".toCharArray(),
                "############".toCharArray()
            ),
            // 关卡8 - 复杂布局
            arrayOf(
                "##############".toCharArray(),
                "#............#".toCharArray(),
                "#..$.......$.#".toCharArray(),
                "#............#".toCharArray(),
                "#.....##.....#".toCharArray(),
                "#.....@......#".toCharArray(),
                "#.....##.....#".toCharArray(),
                "#............#".toCharArray(),
                "#..O.......O.#".toCharArray(),
                "#............#".toCharArray(),
                "##############".toCharArray()
            ),
            // 关卡9 - 紧凑布局
            arrayOf(
                "########".toCharArray(),
                "#......#".toCharArray(),
                "#.O$\$O.#".toCharArray(),
                "#..@@..#".toCharArray(),
                "#......#".toCharArray(),
                "########".toCharArray()
            ),
            // 关卡10 - 多箱子挑战
            arrayOf(
                "###############".toCharArray(),
                "#.............#".toCharArray(),
                "#.$.$.$.$.$...#".toCharArray(),
                "#.............#".toCharArray(),
                "#.......@.....#".toCharArray(),
                "#.............#".toCharArray(),
                "#.O.O.O.O.O...#".toCharArray(),
                "#.............#".toCharArray(),
                "###############".toCharArray()
            )
        )
        
        return if (levelNum <= levels.size) {
            levels[levelNum - 1]
        } else {
            // 超过预设关卡后随机选择一个关卡
            val randomIndex = kotlin.random.Random.nextInt(levels.size)
            levels[randomIndex]
        }
    }

    private fun findPlayerPosition() {
        for (row in gameBoard.indices) {
            for (col in gameBoard[row].indices) {
                if (gameBoard[row][col] == '@') {
                    playerRow = row
                    playerCol = col
                    return
                }
            }
        }
    }

    private fun movePlayer(deltaRow: Int, deltaCol: Int) {
        if (isGameWon) return

        val newRow = playerRow + deltaRow
        val newCol = playerCol + deltaCol

        if (newRow < 0 || newRow >= gameBoard.size || newCol < 0 || newCol >= gameBoard[0].size) {
            return
        }

        val nextCell = gameBoard[newRow][newCol]

        when (nextCell) {
            '#' -> return
            '.', 'O' -> {
                gameBoard[playerRow][playerCol] = if (isTarget(playerRow, playerCol)) 'O' else '.'
                playerRow = newRow
                playerCol = newCol
                gameBoard[playerRow][playerCol] = '@'
                moves++
            }
            '$', '*' -> {
                val boxNewRow = newRow + deltaRow
                val boxNewCol = newCol + deltaCol

                if (boxNewRow < 0 || boxNewRow >= gameBoard.size || 
                    boxNewCol < 0 || boxNewCol >= gameBoard[0].size) {
                    return
                }

                val boxNextCell = gameBoard[boxNewRow][boxNewCol]
                if (boxNextCell == '.' || boxNextCell == 'O') {
                    gameBoard[playerRow][playerCol] = if (isTarget(playerRow, playerCol)) 'O' else '.'
                    playerRow = newRow
                    playerCol = newCol
                    gameBoard[playerRow][playerCol] = '@'
                    
                    gameBoard[boxNewRow][boxNewCol] = if (boxNextCell == 'O') '*' else '$'
                    moves++
                }
            }
        }

        checkWinCondition()
        updateStatus()
        gameCanvas.repaint()
    }

    private fun isTarget(row: Int, col: Int): Boolean {
        return if (isInfiniteMode) {
            originalBoard[row][col] == 'O'
        } else {
            val levelBoard = getLevelData(level)
            levelBoard[row][col] == 'O'
        }
    }

    private fun checkWinCondition() {
        val checkBoard = if (isInfiniteMode) originalBoard else getLevelData(level)
        
        // 检查所有目标位置是否都被箱子占据
        for (row in checkBoard.indices) {
            for (col in checkBoard[row].indices) {
                if (checkBoard[row][col] == 'O') {
                    // 这是一个目标位置，检查是否有箱子在上面
                    if (gameBoard[row][col] != '*') {
                        // 目标位置上没有箱子
                        return
                    }
                }
            }
        }
        
        isGameWon = true
        val message = if (isInfiniteMode) {
            "恭喜！您完成了无限火力关卡 $level！用了 $moves 步！"
        } else {
            "恭喜！您完成了第 $level 关！"
        }
        JOptionPane.showMessageDialog(this, message)
        
        // 无限火力模式下自动生成新关卡
        if (isInfiniteMode) {
            SwingUtilities.invokeLater {
                generateInfiniteLevel()
            }
        }
    }

    private fun updateStatus() {
        val totalLevels = 10
        val modeText = if (isInfiniteMode) "无限火力" else "经典模式"
        val levelDisplay = if (isInfiniteMode) {
            "$level (无限)"
        } else if (level <= totalLevels) {
            "$level/$totalLevels"
        } else {
            "$level (随机)"
        }
        statusLabel.text = "$modeText | 关卡: $levelDisplay | 步数: $moves" + if (isGameWon) " | 已完成！" else ""
    }

    private fun restartLevel() {
        isGameWon = false
        moves = 0
        if (isInfiniteMode) {
            generateInfiniteLevel()
        } else {
            initLevel(level)
        }
        requestFocusInWindow()
    }

    private fun nextLevel() {
        if (isInfiniteMode) {
            generateInfiniteLevel()
        } else {
            level++
            initLevel(level)
        }
    }

    private fun randomLevel() {
        val totalLevels = 10
        level = kotlin.random.Random.nextInt(1, totalLevels + 1)
        isInfiniteMode = false
        initLevel(level)
        requestFocusInWindow()
    }

    private fun toggleInfiniteMode() {
        isInfiniteMode = !isInfiniteMode
        if (isInfiniteMode) {
            generateInfiniteLevel()
        } else {
            level = 1
            initLevel(level)
        }
    }

    private fun generateInfiniteLevel() {
        level = kotlin.random.Random.nextInt(1000, 9999) // 随机关卡编号
        gameBoard = generateRandomLevel()
        originalBoard = gameBoard.map { it.clone() }.toTypedArray() // 深拷贝原始地图
        isGameWon = false
        moves = 0
        findPlayerPosition()
        updateStatus()
        gameCanvas.repaint()
    }

    private fun generateRandomLevel(): Array<CharArray> {
        var attempts = 0
        val maxAttempts = 10
        
        while (attempts < maxAttempts) {
            attempts++
            val level = createRandomLevelAttempt()
            if (isLevelPlayable(level)) {
                return level
            }
        }
        
        // 如果多次尝试都失败，返回一个简单的可玩关卡
        return createSimpleLevel()
    }

    private fun createRandomLevelAttempt(): Array<CharArray> {
        val width = kotlin.random.Random.nextInt(9, 13) // 稍微缩小范围确保更好的布局
        val height = kotlin.random.Random.nextInt(7, 10)
        val board = Array(height) { CharArray(width) { '.' } }
        
        // 创建边界墙
        for (i in 0 until height) {
            board[i][0] = '#'
            board[i][width - 1] = '#'
        }
        for (j in 0 until width) {
            board[0][j] = '#'
            board[height - 1][j] = '#'
        }
        
        // 减少内部墙壁数量，避免分割区域
        val wallCount = kotlin.random.Random.nextInt(1, 3) // 进一步减少墙壁
        repeat(wallCount) {
            val row = kotlin.random.Random.nextInt(3, height - 3) // 远离边界
            val col = kotlin.random.Random.nextInt(3, width - 3)
            // 确保不会创建完全封闭的区域
            if (canPlaceWall(board, row, col)) {
                board[row][col] = '#'
            }
        }
        
        // 放置玩家在中心区域
        var playerPlaced = false
        val centerRow = height / 2
        val centerCol = width / 2
        val searchRadius = 2
        
        for (r in (centerRow - searchRadius)..(centerRow + searchRadius)) {
            for (c in (centerCol - searchRadius)..(centerCol + searchRadius)) {
                if (r in 2 until height - 2 && c in 2 until width - 2 && board[r][c] == '.') {
                    board[r][c] = '@'
                    playerPlaced = true
                    break
                }
            }
            if (playerPlaced) break
        }
        
        if (!playerPlaced) {
            // 备选方案：在安全区域随机放置
            for (i in 2 until height - 2) {
                for (j in 2 until width - 2) {
                    if (board[i][j] == '.') {
                        board[i][j] = '@'
                        playerPlaced = true
                        break
                    }
                }
                if (playerPlaced) break
            }
        }
        
        // 获取所有可推动的位置（远离墙壁和角落）
        val pushablePositions = getPushablePositions(board)
        val safeTargetPositions = getSafeTargetPositions(board)
        
        // 确保有足够的安全位置
        val maxBoxes = kotlin.math.min(6, kotlin.math.min(pushablePositions.size, safeTargetPositions.size))
        val boxCount = kotlin.random.Random.nextInt(2, maxBoxes + 1)
        
        if (pushablePositions.size >= boxCount && safeTargetPositions.size >= boxCount) {
            // 随机选择目标位置
            val selectedTargets = safeTargetPositions.shuffled().take(boxCount)
            selectedTargets.forEach { (row, col) ->
                board[row][col] = 'O'
            }
            
            // 随机选择箱子位置（确保可推动）
            val selectedBoxes = pushablePositions.shuffled().take(boxCount)
            selectedBoxes.forEach { (row, col) ->
                board[row][col] = '$'
            }
        }
        
        return board
    }

    private fun getPushablePositions(board: Array<CharArray>): List<Pair<Int, Int>> {
        val pushablePositions = mutableListOf<Pair<Int, Int>>()
        
        for (i in 2 until board.size - 2) {
            for (j in 2 until board[0].size - 2) {
                if (board[i][j] == '.' && canPushFromPosition(board, i, j)) {
                    pushablePositions.add(Pair(i, j))
                }
            }
        }
        
        return pushablePositions
    }

    private fun getSafeTargetPositions(board: Array<CharArray>): List<Pair<Int, Int>> {
        val safePositions = mutableListOf<Pair<Int, Int>>()
        
        for (i in 2 until board.size - 2) {
            for (j in 2 until board[0].size - 2) {
                if (board[i][j] == '.' && !isCornerPosition(board, i, j) && !isAgainstWall(board, i, j)) {
                    safePositions.add(Pair(i, j))
                }
            }
        }
        
        return safePositions
    }

    private fun canPushFromPosition(board: Array<CharArray>, row: Int, col: Int): Boolean {
        // 检查四个方向是否都有足够的空间进行推动
        val directions = listOf(
            Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
        )
        
        var validDirections = 0
        
        for ((dr, dc) in directions) {
            val fromRow = row - dr
            val fromCol = col - dc
            val toRow = row + dr
            val toCol = col + dc
            
            // 检查是否可以从fromPos推箱子到toPos
            if (isValidPosition(board, fromRow, fromCol) && 
                isValidPosition(board, toRow, toCol) &&
                board[fromRow][fromCol] == '.' && 
                board[toRow][toCol] == '.') {
                validDirections++
            }
        }
        
        return validDirections >= 2 // 至少有两个方向可以推动
    }

    private fun isCornerPosition(board: Array<CharArray>, row: Int, col: Int): Boolean {
        // 检查是否是角落位置（被两个垂直的墙包围）
        val adjacentWalls = mutableListOf<Pair<Int, Int>>()
        
        val directions = listOf(
            Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
        )
        
        for ((dr, dc) in directions) {
            val checkRow = row + dr
            val checkCol = col + dc
            if (isValidPosition(board, checkRow, checkCol) && board[checkRow][checkCol] == '#') {
                adjacentWalls.add(Pair(dr, dc))
            }
        }
        
        // 检查是否有垂直的墙（形成角落）
        return adjacentWalls.any { (dr1, dc1) ->
            adjacentWalls.any { (dr2, dc2) ->
                (dr1 == 0 && dc1 != 0 && dr2 != 0 && dc2 == 0) ||
                (dr1 != 0 && dc1 == 0 && dr2 == 0 && dc2 != 0)
            }
        }
    }

    private fun isAgainstWall(board: Array<CharArray>, row: Int, col: Int): Boolean {
        // 检查是否紧贴墙壁
        val directions = listOf(
            Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
        )
        
        for ((dr, dc) in directions) {
            val checkRow = row + dr
            val checkCol = col + dc
            if (isValidPosition(board, checkRow, checkCol) && board[checkRow][checkCol] == '#') {
                return true
            }
        }
        return false
    }

    private fun isValidPosition(board: Array<CharArray>, row: Int, col: Int): Boolean {
        return row >= 0 && row < board.size && col >= 0 && col < board[0].size
    }

    private fun canPlaceWall(board: Array<CharArray>, row: Int, col: Int): Boolean {
        // 避免在边界附近放置墙壁
        if (row <= 1 || row >= board.size - 2 || col <= 1 || col >= board[0].size - 2) {
            return false
        }
        
        // 避免创建2x2的墙壁块
        val neighbors = listOf(
            Pair(row - 1, col), Pair(row + 1, col),
            Pair(row, col - 1), Pair(row, col + 1)
        )
        
        val wallNeighbors = neighbors.count { (r, c) ->
            r >= 0 && r < board.size && c >= 0 && c < board[0].size && 
            (board[r][c] == '#')
        }
        
        return wallNeighbors <= 1
    }

    private fun isLevelPlayable(board: Array<CharArray>): Boolean {
        // 检查是否有玩家、箱子和目标
        var hasPlayer = false
        var boxCount = 0
        var targetCount = 0
        val boxPositions = mutableListOf<Pair<Int, Int>>()
        val targetPositions = mutableListOf<Pair<Int, Int>>()
        
        for (i in board.indices) {
            for (j in board[i].indices) {
                when (board[i][j]) {
                    '@' -> hasPlayer = true
                    '$' -> {
                        boxCount++
                        boxPositions.add(Pair(i, j))
                    }
                    'O' -> {
                        targetCount++
                        targetPositions.add(Pair(i, j))
                    }
                }
            }
        }
        
        // 基本检查
        if (!hasPlayer || boxCount == 0 || boxCount != targetCount || boxCount > 6) {
            return false
        }
        
        // 检查所有箱子是否可推动
        for ((row, col) in boxPositions) {
            if (isBoxStuck(board, row, col)) {
                return false
            }
        }
        
        // 检查所有目标位置是否可达
        for ((row, col) in targetPositions) {
            if (isCornerPosition(board, row, col)) {
                return false
            }
        }
        
        return true
    }

    private fun isBoxStuck(board: Array<CharArray>, row: Int, col: Int): Boolean {
        // 检查箱子是否被卡住无法推动
        
        // 1. 检查是否在角落
        if (isCornerPosition(board, row, col)) {
            return true
        }
        
        // 2. 检查是否紧贴墙壁且无法推动
        val directions = listOf(
            Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
        )
        
        var canBePushed = false
        
        for ((dr, dc) in directions) {
            val fromRow = row - dr
            val fromCol = col - dc
            val toRow = row + dr
            val toCol = col + dc
            
            // 检查是否可以从某个方向推动到另一个方向
            if (isValidPosition(board, fromRow, fromCol) && 
                isValidPosition(board, toRow, toCol) &&
                board[fromRow][fromCol] == '.' && 
                (board[toRow][toCol] == '.' || board[toRow][toCol] == 'O')) {
                canBePushed = true
                break
            }
        }
        
        return !canBePushed
    }

    private fun createSimpleLevel(): Array<CharArray> {
        // 创建一个简单但可玩的关卡作为备选
        return arrayOf(
            "#########".toCharArray(),
            "#.......#".toCharArray(),
            "#.O...O.#".toCharArray(),
            "#...@...#".toCharArray(),
            "#.......#".toCharArray(),
            "#..$.$..#".toCharArray(),
            "#.......#".toCharArray(),
            "#########".toCharArray()
        )
    }

    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_UP, KeyEvent.VK_W -> movePlayer(-1, 0)
            KeyEvent.VK_DOWN, KeyEvent.VK_S -> movePlayer(1, 0)
            KeyEvent.VK_LEFT, KeyEvent.VK_A -> movePlayer(0, -1)
            KeyEvent.VK_RIGHT, KeyEvent.VK_D -> movePlayer(0, 1)
            KeyEvent.VK_R -> restartLevel()
            KeyEvent.VK_N -> nextLevel()
            KeyEvent.VK_SPACE -> randomLevel()
        }
    }

    override fun keyReleased(e: KeyEvent) {}
    override fun keyTyped(e: KeyEvent) {}

    inner class GameCanvas : JPanel() {
        private var cellSize = 40
        private var offsetX = 0
        private var offsetY = 0

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // 计算自适应单元格大小和偏移量
            calculateSizeAndOffset()

            for (row in gameBoard.indices) {
                for (col in gameBoard[row].indices) {
                    val x = offsetX + col * cellSize
                    val y = offsetY + row * cellSize

                    when (gameBoard[row][col]) {
                        '#' -> {
                            g2d.color = Color.DARK_GRAY
                            g2d.fillRect(x, y, cellSize, cellSize)
                        }
                        '.' -> {
                            g2d.color = Color.LIGHT_GRAY
                            g2d.fillRect(x, y, cellSize, cellSize)
                        }
                        'O' -> {
                            g2d.color = Color.LIGHT_GRAY
                            g2d.fillRect(x, y, cellSize, cellSize)
                            g2d.color = Color.RED
                            val margin = cellSize / 8
                            g2d.drawOval(x + margin, y + margin, cellSize - 2 * margin, cellSize - 2 * margin)
                        }
                        '@' -> {
                            g2d.color = if (isTarget(row, col)) Color.LIGHT_GRAY else Color.LIGHT_GRAY
                            g2d.fillRect(x, y, cellSize, cellSize)
                            if (isTarget(row, col)) {
                                g2d.color = Color.RED
                                val margin = cellSize / 8
                                g2d.drawOval(x + margin, y + margin, cellSize - 2 * margin, cellSize - 2 * margin)
                            }
                            g2d.color = Color.BLUE
                            val margin = cellSize / 5
                            g2d.fillOval(x + margin, y + margin, cellSize - 2 * margin, cellSize - 2 * margin)
                        }
                        '$' -> {
                            g2d.color = Color.LIGHT_GRAY
                            g2d.fillRect(x, y, cellSize, cellSize)
                            g2d.color = Color.ORANGE
                            val margin = cellSize / 8
                            g2d.fillRect(x + margin, y + margin, cellSize - 2 * margin, cellSize - 2 * margin)
                        }
                        '*' -> {
                            g2d.color = Color.LIGHT_GRAY
                            g2d.fillRect(x, y, cellSize, cellSize)
                            g2d.color = Color.RED
                            val margin = cellSize / 8
                            g2d.drawOval(x + margin, y + margin, cellSize - 2 * margin, cellSize - 2 * margin)
                            g2d.color = Color.GREEN
                            val innerMargin = cellSize / 5
                            g2d.fillRect(x + innerMargin, y + innerMargin, cellSize - 2 * innerMargin, cellSize - 2 * innerMargin)
                        }
                    }

                    g2d.color = Color.BLACK
                    g2d.drawRect(x, y, cellSize, cellSize)
                }
            }
        }

        private fun calculateSizeAndOffset() {
            val panelWidth = width
            val panelHeight = height
            
            if (panelWidth <= 0 || panelHeight <= 0 || gameBoard.isEmpty()) {
                cellSize = 40
                offsetX = 0
                offsetY = 0
                return
            }
            
            val boardWidth = gameBoard[0].size
            val boardHeight = gameBoard.size
            
            // 计算最大可能的单元格大小，保持地图完全可见
            val maxCellWidth = panelWidth / boardWidth
            val maxCellHeight = panelHeight / boardHeight
            cellSize = kotlin.math.min(maxCellWidth, maxCellHeight).coerceAtLeast(20) // 最小单元格大小20
            
            // 计算居中偏移量
            val totalGameWidth = boardWidth * cellSize
            val totalGameHeight = boardHeight * cellSize
            offsetX = (panelWidth - totalGameWidth) / 2
            offsetY = (panelHeight - totalGameHeight) / 2
        }

        override fun getPreferredSize(): Dimension {
            return Dimension(800, 600) // 设置一个合理的默认大小
        }
    }
}
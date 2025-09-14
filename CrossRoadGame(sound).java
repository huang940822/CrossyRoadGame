import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;

public class CrossyRoadGame extends JFrame implements KeyListener {
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;
    private static final int TILE_SIZE = 40;
    private static final int ROAD_WIDTH = WINDOW_WIDTH;
    
    private GamePanel gamePanel;
    private Timer gameTimer;
    private Player player;
    private ArrayList<Car> cars;
    private ArrayList<Road> roads;
    private Random random;
    private int score;
    private boolean gameRunning;
    
    // 攝影機系統
    private double cameraX; // 攝影機X位置
    private double cameraY; // 攝影機Y位置
    private double targetCameraX; // 目標攝影機X位置
    private double targetCameraY; // 目標攝影機Y位置
    private static final double CAMERA_FOLLOW_SPEED = 0.08; // 攝影機跟隨速度
    private static final double CAMERA_DEADZONE_X = 100; // X軸死區範圍
    private static final double CAMERA_DEADZONE_Y = 100; // Y軸死區範圍
    
    private boolean gameStarted; // 是否已離開安全區開始遊戲
    private double deathLineWorldY; // 死亡線的世界Y位置
    private static final double DEATH_LINE_SPEED = 0.8; // 死亡線移動速度
    private static final int SAFE_ZONE_SIZE = 3; // 安全區域大小（底部幾排）
    private static final boolean DEBUG_MODE = false; // 調試模式，顯示碰撞框
    
    // 隨機安全區相關
    private static final double SAFE_ZONE_PROBABILITY = 0.15; // 15% 機率生成安全區
    private static final int MIN_SAFE_ZONE_INTERVAL = 8; // 安全區最小間隔
    private static final int MAX_SAFE_ZONE_INTERVAL = 15; // 安全區最大間隔
    private int lastSafeZoneIndex = -100; // 上一個安全區的位置
    
    // 世界邊界
    private static final int WORLD_LEFT_BOUNDARY = -WINDOW_WIDTH;
    private static final int WORLD_RIGHT_BOUNDARY = WINDOW_WIDTH * 2;

    private BackgroundMusic musicPlayer; // 新增：儲存音樂播放器的實例
    
    public CrossyRoadGame() {
        setTitle("天天過馬路 - 隨機安全區版本");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);
        
        // 初始化遊戲物件
        random = new Random();
        cars = new ArrayList<>();
        roads = new ArrayList<>();

        musicPlayer = new BackgroundMusic(); // 初始化
        musicPlayer.playMusic("breakbeat-generation-instrumental.wav"); // 播放原始音樂
        
        // 玩家初始位置 - 世界座標系
        player = new Player(WINDOW_WIDTH / 2, 0); // Y=0 是起始線
        
        // 攝影機初始位置 - 只跟隨Y軸
        cameraY = player.y - WINDOW_HEIGHT * 0.7; // 玩家在螢幕下方30%的位置
        targetCameraY = cameraY;
        
        score = 0;
        gameRunning = true;
        gameStarted = false;
        deathLineWorldY = 200; // 死亡線從玩家下方開始
        
        // 創建道路
        createRoads();
        
        // 初始化車輛
        initializeCars();
        
        // 創建遊戲面板
        gamePanel = new GamePanel();
        add(gamePanel);
        
        // 添加鍵盤監聽
        addKeyListener(this);
        setFocusable(true);
        
        // 創建遊戲計時器
        gameTimer = new Timer(16, new ActionListener() { // 約60FPS
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGame();
                gamePanel.repaint();
            }
        });
        
        gameTimer.start();
    }
    
    private void createRoads() {
        // 創建更多道路用於無限世界
        for (int i = -10; i < 50; i++) { // 從負數開始，確保有足夠的道路
            int y = i * TILE_SIZE;
            boolean isSafeZone = shouldCreateSafeZone(i);
            roads.add(new Road(y, random.nextBoolean(), i, isSafeZone));
        }
    }
    
    // 判斷是否應該創建安全區
    private boolean shouldCreateSafeZone(int roadIndex) {
        // 起始區域周圍不創建隨機安全區
        if (roadIndex >= -SAFE_ZONE_SIZE && roadIndex <= SAFE_ZONE_SIZE) {
            return false;
        }
        
        // 檢查與上一個安全區的距離
        int distanceFromLastSafeZone = Math.abs(roadIndex - lastSafeZoneIndex);
        if (distanceFromLastSafeZone < MIN_SAFE_ZONE_INTERVAL) {
            return false;
        }
        
        // 如果距離足夠遠，有一定機率創建安全區
        if (distanceFromLastSafeZone >= MAX_SAFE_ZONE_INTERVAL) {
            // 強制創建安全區，避免過長時間沒有安全區
            lastSafeZoneIndex = roadIndex;
            return true;
        }
        
        // 正常機率判斷
        if (random.nextDouble() < SAFE_ZONE_PROBABILITY) {
            lastSafeZoneIndex = roadIndex;
            return true;
        }
        
        return false;
    }
    
    private void initializeCars() {
        // 在每條道路上隨機放置一些車輛，但跳過所有安全區域
        for (Road road : roads) {
            // 跳過所有安全區域（包括起始區和隨機安全區）
            if (road.isSafeZone || (road.roadIndex >= -SAFE_ZONE_SIZE && road.roadIndex <= SAFE_ZONE_SIZE)) {
                continue;
            }
            
            int numCars = random.nextInt(4) + 2; // 每條路2-5輛車，增加初始車輛數量
            for (int i = 0; i < numCars; i++) {
                Car car = new Car(road);
                // 設置車輛初始位置 - 分散在整個道路寬度範圍內
                if (road.rightDirection) {
                    car.x = random.nextInt(WINDOW_WIDTH * 2) - WINDOW_WIDTH;
                } else {
                    car.x = random.nextInt(WINDOW_WIDTH * 2) - WINDOW_WIDTH / 2;
                }
                cars.add(car);
            }
        }
    }
    
    private void updateGame() {
        if (!gameRunning) return;
        
        // 檢查玩家是否離開安全區
        if (!gameStarted && player.y < -SAFE_ZONE_SIZE * TILE_SIZE) {
            gameStarted = true;
        }
        
        // 更新攝影機目標位置 - 平滑跟隨玩家
        updateCamera();
        
        // 只有離開安全區後死亡線才開始移動
        if (gameStarted) {
            deathLineWorldY -= DEATH_LINE_SPEED; // 死亡線向上移動（Y座標減少）
            
            // 檢查玩家是否被死亡線追上
            if (player.y >= deathLineWorldY) { // 玩家在死亡線下方就被追上
                gameRunning = false;
                JOptionPane.showMessageDialog(this, "被死亡線追上了！得分：" + score);
                resetGame();
                return;
            }
        }
        
        // 動態生成新道路
        generateNewRoads();
        
        // 生成新車輛
        generateNewCars();
        
        // 更新車輛位置
        updateCars();
        
        // 清理舊道路和車輛
        cleanup();
        
        // 檢查碰撞
        checkCollisions();
        
        // 更新分數 - 玩家向上移動分數增加
        score = Math.max(score, (int)(-player.y / TILE_SIZE));
    }
    
    private void updateCamera() {
        // Y軸攝影機跟隨 - 保持玩家在螢幕下方
        targetCameraY = player.y - WINDOW_HEIGHT * 0.7;
        
        // 平滑移動攝影機到目標位置（只有Y軸）
        double cameraSpeedY = (targetCameraY - cameraY) * CAMERA_FOLLOW_SPEED;
        cameraY += cameraSpeedY;
    }
    
    private void generateNewRoads() {
        // 找到最高和最低的道路
        int highestRoadIndex = roads.isEmpty() ? 0 : roads.get(0).roadIndex;
        int lowestRoadIndex = roads.isEmpty() ? 0 : roads.get(0).roadIndex;
        
        for (Road road : roads) {
            if (road.roadIndex > highestRoadIndex) {
                highestRoadIndex = road.roadIndex;
            }
            if (road.roadIndex < lowestRoadIndex) {
                lowestRoadIndex = road.roadIndex;
            }
        }
        
        // 根據玩家位置生成新道路
        int playerRoadIndex = (int)(player.y / TILE_SIZE);
        
        // 向上生成道路 - 預先生成更多道路
        while (highestRoadIndex < playerRoadIndex - 25) { // 在玩家前方25格就開始生成
            highestRoadIndex++;
            int y = highestRoadIndex * TILE_SIZE;
            boolean isSafeZone = shouldCreateSafeZone(highestRoadIndex);
            Road newRoad = new Road(y, random.nextBoolean(), highestRoadIndex, isSafeZone);
            roads.add(newRoad);
            
            // 只在非安全區域生成車輛
            if (!newRoad.isSafeZone && !(newRoad.roadIndex >= -SAFE_ZONE_SIZE && newRoad.roadIndex <= SAFE_ZONE_SIZE)) {
                int numCars = random.nextInt(3) + 2; // 新道路2-4輛車
                for (int i = 0; i < numCars; i++) {
                    Car car = new Car(newRoad);
                    // 在道路可見範圍內隨機分布車輛
                    if (newRoad.rightDirection) {
                        car.x = random.nextInt(WINDOW_WIDTH * 2) - WINDOW_WIDTH;
                    } else {
                        car.x = random.nextInt(WINDOW_WIDTH * 2) - WINDOW_WIDTH / 2;
                    }
                    cars.add(car);
                }
            }
        }
        
        // 向下生成道路（如果需要）
        while (lowestRoadIndex > playerRoadIndex - 10) {
            lowestRoadIndex--;
            int y = lowestRoadIndex * TILE_SIZE;
            boolean isSafeZone = shouldCreateSafeZone(lowestRoadIndex);
            Road newRoad = new Road(y, random.nextBoolean(), lowestRoadIndex, isSafeZone);
            roads.add(newRoad);
        }
    }
    
    private void generateNewCars() {
        // 計算每條道路上的車輛數量
        java.util.Map<Road, Integer> roadCarCounts = new java.util.HashMap<>();
        for (Road road : roads) {
            roadCarCounts.put(road, 0);
        }
        
        // 統計每條道路上的車輛數量
        for (Car car : cars) {
            if (roadCarCounts.containsKey(car.road)) {
                roadCarCounts.put(car.road, roadCarCounts.get(car.road) + 1);
            }
        }
        
        for (Road road : roads) {
            // 安全區域和起始安全區域都不生成車輛
            if (road.isSafeZone || (road.roadIndex >= -SAFE_ZONE_SIZE && road.roadIndex <= SAFE_ZONE_SIZE)) {
                continue;
            }
            
            // 檢查道路是否在攝影機附近或玩家前方
            double roadScreenY = road.y - cameraY;
            double playerRoadDistance = Math.abs(road.y - player.y) / TILE_SIZE;
            
            // 擴大生成範圍 - 包括玩家前方的道路
            if (roadScreenY > WINDOW_HEIGHT + TILE_SIZE * 5 && playerRoadDistance > 30) {
                continue; // 太遠且不在玩家前方的道路不處理
            }
            
            // 更新道路狀態
            road.update();
            
            int currentCarCount = roadCarCounts.getOrDefault(road, 0);
            
            // 如果道路上車輛太少，強制生成車輛
            if (road.needsMoreCars(currentCarCount)) {
                Car car = new Car(road);
                cars.add(car);
            }
            // 否則根據正常的生成邏輯
            else if (road.shouldSpawnCar() && !road.hasTooManyCars(currentCarCount)) {
                // 額外檢查：如果玩家正在快速接近這條道路，提高生成機率
                if (playerRoadDistance < 10 && player.y < road.y) { // 玩家在道路下方且距離較近
                    if (random.nextDouble() < 0.8) { // 80% 機率生成
                        Car car = new Car(road);
                        cars.add(car);
                    }
                } else if (random.nextDouble() < 0.6) { // 一般情況60% 機率生成
                    Car car = new Car(road);
                    cars.add(car);
                }
            }
        }
    }
    
    private void updateCars() {
        for (int i = cars.size() - 1; i >= 0; i--) {
            Car car = cars.get(i);
            car.update();
            
            // 移除太遠的車輛
            if (car.x < -Car.CAR_WIDTH * 2 || car.x > WINDOW_WIDTH + Car.CAR_WIDTH * 2) {
                cars.remove(i);
            }
        }
    }
    
    private void cleanup() {
        // 清理太遠的道路，但保留更多玩家前方的道路
        int playerRoadIndex = (int)(player.y / TILE_SIZE);
        roads.removeIf(road -> road.roadIndex > playerRoadIndex + 40 || road.roadIndex < playerRoadIndex - 20);
    }
    
        private void checkCollisions() {
        // 檢查玩家是否在安全區域
        boolean playerInSafeZone = false;
        int playerRoadIndex = (int)(player.y / TILE_SIZE);
        
        // 檢查起始安全區
        if (playerRoadIndex >= -SAFE_ZONE_SIZE && playerRoadIndex <= SAFE_ZONE_SIZE) {
            playerInSafeZone = true;
        }
        
        // 檢查隨機安全區
        for (Road road : roads) {
            if (road.isSafeZone && Math.abs(road.y - player.y) < TILE_SIZE / 2) {
                playerInSafeZone = true;
                break;
            }
        }
        
        // 如果玩家在安全區域，不進行碰撞檢測
        if (playerInSafeZone) {
            return;
        }
        
        // 玩家碰撞框
        Rectangle playerRect = new Rectangle(
            (int)(player.x - TILE_SIZE/2 + 2), 
            (int)(player.y - TILE_SIZE/2 + 2), 
            TILE_SIZE - 4, 
            TILE_SIZE - 4
        );
        
        for (Car car : cars) {
            // 檢查車輛是否與玩家在同一行且距離較近
            if (Math.abs(car.road.y - player.y) < TILE_SIZE && 
                Math.abs(car.x - player.x) < Car.CAR_WIDTH + TILE_SIZE) {
                
                Rectangle carRect = car.getBounds();
                
                if (playerRect.intersects(carRect)) {
                    // **碰撞發生！**
                    gameRunning = false; // 設定遊戲結束狀態
                    
                    //立即停止當前背景音樂
                    if (musicPlayer != null) {
                        musicPlayer.stopMusic();
                    }
                    
                    // 立即嘗試播放遊戲結束音效
                    try {
                        // 請務必確認 'sonic-1-music-game-over.wav' 的檔案路徑是正確的！
                        // 如果檔案在專案根目錄，請使用 'new File("sonic-1-music-game-over.wav")'
                        // 如果檔案在 src/resources 下，請使用 'getClass().getResourceAsStream("/resources/sonic-1-music-game-over.wav")'
                        File gameOverSoundFile = new File("pou-game-over-sound-effect.wav"); 
                        
                        AudioInputStream audioStream;
                        Clip gameOverClip = null; 

                        if (gameOverSoundFile.exists()) {
                            audioStream = AudioSystem.getAudioInputStream(gameOverSoundFile);
                        } else {
                            // 如果檔案不存在，嘗試從資源載入 (打包 JAR 時常用)
                            java.io.InputStream resourceStream = getClass().getResourceAsStream("/resources/pou-game-over-sound-effect.wav"); // 假設在 src/resources
                            if (resourceStream != null) {
                                audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(resourceStream));
                                System.out.println("偵測到遊戲結束音效資源存在");
                            } else {
                                System.err.println("錯誤: 無法找到遊戲結束音效檔。");
                                // 如果找不到音效，直接跳過音效播放，並顯示消息框、重置遊戲
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(this, "撞到車輛！得分：" + score);
                                    resetGame();
                                });
                                return; // 結束碰撞處理，退出整個 checkCollisions 方法
                            }
                        }

                        gameOverClip = AudioSystem.getClip();
                        gameOverClip.open(audioStream);

                        // 關鍵：為 gameOverClip 添加監聽器，確保在音效播放結束後才執行後續邏輯
                        final Clip finalGameOverClip = gameOverClip; // 為了在 Lambda 表達式中使用
                        gameOverClip.addLineListener(event -> {
                            if (event.getType() == LineEvent.Type.STOP) {
                                finalGameOverClip.close(); // **播放完畢後關閉 Clip，釋放資源**
                                
                                // **在音效播放完畢後，才在 AWT 執行緒上顯示消息框並重置遊戲**
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(this, "撞到車輛！得分：" + score);
                                    resetGame(); // 遊戲重置
                                });
                            }
                        });

                        gameOverClip.start(); // **立即開始播放遊戲結束音效**

                    } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
                        ex.printStackTrace();
                        System.err.println("無法播放遊戲結束音效。");
                        // 如果播放音效失敗，仍然顯示消息框並重置遊戲
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "撞到車輛！得分：" + score);
                            resetGame();
                        });
                    }
                    return; 
                }
            }
        }
    }
    class BackgroundMusic {
        private Clip clip;

        public void playMusic(String filepath) {
            try {
                // 第一層檢查：嘗試以檔案方式載入 (適用於開發階段，檔案在專案根目錄或絕對路徑)
                File audioFile = new File(filepath);
                if (audioFile.exists()) {
                    AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);

                    clip = AudioSystem.getClip();
                    clip.open(audioStream); // 嘗試打開音訊串流
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                    clip.start();
                    return; // 成功播放後直接返回
                } else {
                    System.out.println("檔案不存在於此路徑 (嘗試資源載入): " + audioFile.getAbsolutePath());
                }

                clip = AudioSystem.getClip();
                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();

            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                e.printStackTrace();
                System.err.println("無法播放音樂" + filepath);
            }
        }

        public void stopMusic() {
            if (clip != null && clip.isRunning()) {
                clip.stop();
                clip.close();
            }
        }

    }
    
    private void resetGame() {
        player = new Player(WINDOW_WIDTH / 2, 0);
        
        // 重置攝影機（只有Y軸）
        cameraY = player.y - WINDOW_HEIGHT * 0.7;
        targetCameraY = cameraY;
        
        cars.clear();
        roads.clear();
        score = 0;
        gameStarted = false;
        deathLineWorldY = 200;
        gameRunning = true;
        lastSafeZoneIndex = -100; // 重置安全區記錄
        createRoads();
        initializeCars();
        
        if (musicPlayer != null) {
        //musicPlayer.stopMusic(); // 停止任何可能正在播放的音樂
        musicPlayer.playMusic("breakbeat-generation-instrumental.wav"); // 重新播放原始音樂
        } else {
            // 如果 musicPlayer 為 null (不應該發生，但作為防禦性程式碼)
            musicPlayer = new BackgroundMusic();
            musicPlayer.playMusic("breakbeat-generation-instrumental.wav");
        }
    }
    
    private boolean[] keysPressed = new boolean[256];
    
    @Override
    public void keyPressed(KeyEvent e) {
        if (!gameRunning) return;
        
        int key = e.getKeyCode();
        
        // 防止按鍵重複觸發
        if (keysPressed[key]) return;
        keysPressed[key] = true;
        
        switch (key) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                player.y -= TILE_SIZE; // 向上移動（Y座標減少）
                break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                player.y += TILE_SIZE; // 向下移動（Y座標增加）
                break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                if (player.x > WORLD_LEFT_BOUNDARY) {
                    player.x -= TILE_SIZE;
                }
                break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                if (player.x < WORLD_RIGHT_BOUNDARY) {
                    player.x += TILE_SIZE;
                }
                break;
        }
    }
    
    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key < keysPressed.length) {
            keysPressed[key] = false;
        }
    }
    
    @Override
    public void keyTyped(KeyEvent e) {}
    
    // 內部類別：遊戲面板
    class GamePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            
            // 背景
            g.setColor(Color.GREEN);
            g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
            
            // 繪製道路（根據攝影機位置調整）
            for (Road road : roads) {
                int drawY = (int)(road.y - cameraY);
                
                // 只繪製可見的道路
                if (drawY > -TILE_SIZE && drawY < WINDOW_HEIGHT + TILE_SIZE) {
                    // 根據道路類型選擇顏色
                    if (road.isSafeZone) {
                        // 安全區域用綠色標示
                        g.setColor(new Color(34, 139, 34)); // 森林綠
                    } else if (road.roadIndex >= -SAFE_ZONE_SIZE && road.roadIndex <= SAFE_ZONE_SIZE) {
                        // 起始安全區域用淺灰色標示
                        g.setColor(Color.LIGHT_GRAY);
                    } else {
                        // 普通道路用灰色
                        g.setColor(Color.GRAY);
                    }
                    g.fillRect(0, drawY, WINDOW_WIDTH, TILE_SIZE);
                    
                    // 道路邊界線
                    g.setColor(Color.WHITE);
                    g.drawLine(0, drawY, WINDOW_WIDTH, drawY);
                    g.drawLine(0, drawY + TILE_SIZE - 1, WINDOW_WIDTH, drawY + TILE_SIZE - 1);
                    
                    // 安全區域不畫中線，而是畫安全標誌
                    if (road.isSafeZone) {
                        // 繪製安全標誌 - 綠色十字
                        g.setColor(Color.WHITE);
                        int centerY = drawY + TILE_SIZE / 2;
                        for (int x = TILE_SIZE; x < WINDOW_WIDTH; x += TILE_SIZE * 2) {
                            // 十字標誌
                            g.fillRect(x - 8, centerY - 2, 16, 4);
                            g.fillRect(x - 2, centerY - 8, 4, 16);
                        }
                        
                        // 安全區文字提示
                        g.setColor(Color.WHITE);
                        g.setFont(new Font("Arial", Font.BOLD, 14));
                        g.drawString("安全區", 5, drawY + TILE_SIZE / 2 + 5);
                    } else if (!(road.roadIndex >= -SAFE_ZONE_SIZE && road.roadIndex <= SAFE_ZONE_SIZE)) {
                        // 普通道路的中線
                        g.setColor(Color.YELLOW);
                        for (int x = 0; x < WINDOW_WIDTH; x += TILE_SIZE) {
                            g.fillRect(x, drawY + TILE_SIZE / 2 - 2, TILE_SIZE / 2, 4);
                        }
                    }
                    
                    // 顯示道路編號（調試用）
                    if (DEBUG_MODE) {
                        g.setColor(Color.BLACK);
                        g.setFont(new Font("Arial", Font.BOLD, 12));
                        String roadInfo = "路" + road.roadIndex;
                        if (road.isSafeZone) roadInfo += "(安全)";
                        g.drawString(roadInfo, 5, drawY + TILE_SIZE / 2 + 5);
                    }
                }
            }
            
            // 繪製起始線
            int startLineY = (int)(0 - cameraY);
            if (startLineY > -10 && startLineY < WINDOW_HEIGHT + 10) {
                g.setColor(Color.BLUE);
                g.fillRect(0, startLineY - 2, WINDOW_WIDTH, 4);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 16));
                g.drawString("起始線", 10, startLineY - 5);
            }
            
            // 繪製車輛（根據攝影機位置調整）- 長方形車輛
            g.setColor(Color.RED);
            for (Car car : cars) {
                int drawX = (int)(car.x - Car.CAR_WIDTH/2);
                int drawY = (int)(car.y - cameraY - Car.CAR_HEIGHT/2);
                
                // 只繪製可見的車輛
                if (drawX > -Car.CAR_WIDTH && drawX < WINDOW_WIDTH + Car.CAR_WIDTH &&
                    drawY > -Car.CAR_HEIGHT && drawY < WINDOW_HEIGHT + Car.CAR_HEIGHT) {
                    
                    // 繪製車身
                    g.setColor(Color.RED);
                    g.fillRect(drawX, drawY, Car.CAR_WIDTH, Car.CAR_HEIGHT);
                    
                    // 繪製車輛邊框
                    g.setColor(Color.DARK_GRAY);
                    g.drawRect(drawX, drawY, Car.CAR_WIDTH, Car.CAR_HEIGHT);
                    
                    // 繪製車窗
                    g.setColor(Color.CYAN);
                    int windowX = drawX + Car.CAR_WIDTH / 6;
                    int windowY = drawY + Car.CAR_HEIGHT / 4;
                    int windowWidth = Car.CAR_WIDTH * 2 / 3;
                    int windowHeight = Car.CAR_HEIGHT / 2;
                    g.fillRect(windowX, windowY, windowWidth, windowHeight);
                    
                    // 根據移動方向繪製車頭燈
                    g.setColor(Color.YELLOW);
                    if (car.movingRight) {
                        // 右行車輛，車頭燈在右側
                        g.fillOval(drawX + Car.CAR_WIDTH - 8, drawY + 4, 6, 6);
                        g.fillOval(drawX + Car.CAR_WIDTH - 8, drawY + Car.CAR_HEIGHT - 10, 6, 6);
                    } else {
                        // 左行車輛，車頭燈在左側
                        g.fillOval(drawX + 2, drawY + 4, 6, 6);
                        g.fillOval(drawX + 2, drawY + Car.CAR_HEIGHT - 10, 6, 6);
                    }
                }
            }
            
            // 繪製死亡線
            if (gameStarted) {
                int deathLineY = (int)(deathLineWorldY - cameraY);
                if (deathLineY >= -10 && deathLineY <= WINDOW_HEIGHT + 10) {
                    g.setColor(Color.RED);
                    g.fillRect(0, deathLineY - 5, WINDOW_WIDTH, 10);
                    g.setColor(Color.DARK_GRAY);
                    g.drawRect(0, deathLineY - 5, WINDOW_WIDTH - 1, 9);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Arial", Font.BOLD, 16));
                    g.drawString("死亡線", 10, deathLineY + 5);
                }
            }
            
            // 繪製玩家（在螢幕中央）
            int playerDrawX = (int)(player.x - TILE_SIZE/2);
            int playerDrawY = (int)(player.y - cameraY - TILE_SIZE/2);
            
            // 檢查玩家是否在安全區域，改變顏色
            boolean playerInSafeZone = false;
            int playerRoadIndex = (int)(player.y / TILE_SIZE);
            
            // 檢查起始安全區
            if (playerRoadIndex >= -SAFE_ZONE_SIZE && playerRoadIndex <= SAFE_ZONE_SIZE) {
                playerInSafeZone = true;
            }
            
            // 檢查隨機安全區
            for (Road road : roads) {
                if (road.isSafeZone && Math.abs(road.y - player.y) < TILE_SIZE / 2) {
                    playerInSafeZone = true;
                    break;
                }
            }
            
            // 根據是否在安全區域改變玩家顏色
            if (playerInSafeZone) {
                g.setColor(Color.CYAN); // 在安全區域時用青色
            } else {
                g.setColor(Color.BLUE); // 普通狀態用藍色
            }
            g.fillRect(playerDrawX, playerDrawY, TILE_SIZE, TILE_SIZE);
            g.setColor(Color.BLACK);
            g.drawRect(playerDrawX, playerDrawY, TILE_SIZE, TILE_SIZE);
            
            // 調試模式：顯示碰撞框和格線
            if (DEBUG_MODE) {
                // 玩家碰撞框
                g.setColor(Color.MAGENTA);
                g.drawRect(playerDrawX + 2, playerDrawY + 2, TILE_SIZE - 4, TILE_SIZE - 4);
                
                // 顯示格線
                g.setColor(new Color(0, 255, 255, 100));
                int gridOffsetY = (int)(cameraY % TILE_SIZE);
                
                // 垂直線
                for (int x = 0; x < WINDOW_WIDTH; x += TILE_SIZE) {
                    g.drawLine(x, 0, x, WINDOW_HEIGHT);
                }
                
                // 水平線
                for (int y = -gridOffsetY; y < WINDOW_HEIGHT; y += TILE_SIZE) {
                    g.drawLine(0, y, WINDOW_WIDTH, y);
                }
                
                // 攝影機資訊
                g.setColor(Color.BLACK);
                g.setFont(new Font("Arial", Font.BOLD, 12));
                g.drawString("攝影機Y: " + (int)cameraY, 10, WINDOW_HEIGHT - 60);
                g.drawString("玩家世界座標: (" + (int)player.x + ", " + (int)player.y + ")", 10, WINDOW_HEIGHT - 40);
                g.drawString("玩家螢幕座標: (" + playerDrawX + ", " + playerDrawY + ")", 10, WINDOW_HEIGHT - 20);
            }
            
            // 繪製分數和狀態
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString("score: " + score, 10, 30);
            
            // 顯示安全區狀態
            if (playerInSafeZone) {
                g.setColor(Color.GREEN);
                g.setFont(new Font("Arial", Font.BOLD, 18));
                g.drawString("安全區域！", 10, 55);
            }
            
            // 顯示死亡線距離警告
            if (gameStarted) {
                double distanceToDeathLine = deathLineWorldY - player.y; // 修正距離計算
                g.setColor(Color.BLACK);
                g.setFont(new Font("Arial", Font.BOLD, 20));
                g.drawString("死亡線距離: " + String.format("%.1f", distanceToDeathLine / TILE_SIZE), 10, 80);
                
                if (distanceToDeathLine < TILE_SIZE * 5) {
                    g.setColor(Color.RED);
                    g.setFont(new Font("Arial", Font.BOLD, 24));
                    g.drawString("死亡線接近！快向上移動！", WINDOW_WIDTH / 2 - 120, 50);
                }
            }
            
            // 顯示控制說明
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.drawString("WASD 或方向鍵移動", WINDOW_WIDTH - 180, WINDOW_HEIGHT - 40);
            g.drawString("綠色區域 = 安全區", WINDOW_WIDTH - 180, WINDOW_HEIGHT - 20);
            
            // 顯示安全區域提示
            if (!gameStarted) {
                g.setColor(Color.BLUE);
                g.setFont(new Font("Arial", Font.BOLD, 18));
                g.drawString("安全區域 - 向上移動離開安全區開始遊戲！", 10, WINDOW_HEIGHT - 60);
                g.setColor(Color.GREEN);
                g.setFont(new Font("Arial", Font.BOLD, 16));
                g.drawString("遊戲中會隨機出現綠色安全區，可以暫時休息！", 10, WINDOW_HEIGHT - 40);
            }
            
            if (!gameRunning) {
                g.setColor(Color.BLACK);
                g.setFont(new Font("Arial", Font.BOLD, 40));
                g.drawString("Game Over", WINDOW_WIDTH / 2 - 80, WINDOW_HEIGHT / 2);
            }
        }
    }
    
    // 內部類別：玩家
    class Player {
        double x, y;
        
        public Player(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    
    // 內部類別：車輛
    class Car {
        double x, y;
        double speed;
        boolean movingRight;
        Road road;
        static final int CAR_WIDTH = (int)(TILE_SIZE * 1.5); // 車輛寬度
        static final int CAR_HEIGHT = TILE_SIZE; // 車輛高度
        
        public Car(Road road) {
            this.road = road;
            this.y = road.y;
            this.movingRight = road.rightDirection;
            this.speed = road.carSpeed; // 使用道路的固定車速
            
            // 根據方向設定起始位置
            if (movingRight) {
                this.x = -CAR_WIDTH; // 從左邊界外開始
            } else {
                this.x = WINDOW_WIDTH + CAR_WIDTH; // 從右邊界外開始
            }
        }
        
        public void update() {
            if (movingRight) {
                x += speed;
            } else {
                x -= speed;
            }
        }
        
        public Rectangle getBounds() {
            return new Rectangle(
                (int)(x - CAR_WIDTH/2 + 2), 
                (int)(y - CAR_HEIGHT/2 + 2), 
                CAR_WIDTH - 4, 
                CAR_HEIGHT - 4
            );
        }
    }
    
    // 內部類別：道路
    class Road {
        int y;
        boolean rightDirection;
        int roadIndex;
        boolean isSafeZone; // 是否為安全區域
        double carSpeed; // 這條道路上車輛的統一速度
        int carSpawnTimer; // 車輛生成計時器
        int carSpawnInterval; // 車輛生成間隔
        private int lastCarCount; // 上一幀的車輛數量
        private static final int MIN_CARS_PER_ROAD = 1; // 每條路最少車輛數
        private static final int MAX_CARS_PER_ROAD = 4; // 每條路最多車輛數
        
        public Road(int y, boolean rightDirection, int roadIndex, boolean isSafeZone) {
            this.y = y;
            this.rightDirection = rightDirection;
            this.roadIndex = roadIndex;
            this.isSafeZone = isSafeZone;
            
            // 安全區域不需要車速相關設定
            if (!isSafeZone) {
                // 為每條道路設定固定的車速（1.5-4.0之間）
                this.carSpeed = 1.5 + random.nextDouble() * 2.5;
                
                // 設定車輛生成間隔（30-120幀之間，約0.5-2秒）
                this.carSpawnInterval = 30 + random.nextInt(90);
                this.carSpawnTimer = random.nextInt(carSpawnInterval); // 隨機初始計時器
            }
            this.lastCarCount = 0;
        }
        
        public void update() {
            if (!isSafeZone) {
                carSpawnTimer++;
            }
        }
        
        public boolean shouldSpawnCar() {
            if (isSafeZone) return false; // 安全區域不生成車輛
            
            if (carSpawnTimer >= carSpawnInterval) {
                carSpawnTimer = 0;
                return true;
            }
            return false;
        }
        
        // 檢查這條道路是否需要更多車輛
        public boolean needsMoreCars(int currentCarCount) {
            if (isSafeZone) return false; // 安全區域不需要車輛
            return currentCarCount < MIN_CARS_PER_ROAD;
        }
        
        // 檢查這條道路是否車輛過多
        public boolean hasTooManyCars(int currentCarCount) {
            if (isSafeZone) return true; // 安全區域任何車輛都算過多
            return currentCarCount > MAX_CARS_PER_ROAD;
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                CrossyRoadGame game = new CrossyRoadGame();
                game.setVisible(true); // 顯示遊戲視窗
            }
        });
    }
}

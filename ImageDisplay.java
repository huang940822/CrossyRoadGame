import javax.swing.*;
import java.awt.*;

public class ImageDisplay extends JFrame {

    public ImageDisplay() {
        setTitle("圖片顯示（黑色背景）");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 設定背景為黑色的 JPanel
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                setBackground(Color.BLACK); // 設定背景為黑色
            }
        };

        // 使用 BorderLayout 讓圖片置中
        panel.setLayout(new BorderLayout());

        // 載入圖片（支援透明背景的格式：PNG）
        ImageIcon imageIcon = new ImageIcon("Unknown.jpg"); // 請確認圖檔為支援透明的格式（例如 .png）
        JLabel imageLabel = new JLabel(imageIcon);
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);

        panel.add(imageLabel, BorderLayout.CENTER);
        setContentPane(panel);

        pack();
        setLocationRelativeTo(null); // 置中視窗
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ImageDisplay());
    }
}

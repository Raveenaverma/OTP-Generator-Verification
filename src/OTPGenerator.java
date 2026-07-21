import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.regex.Pattern;

// Jakarta Mail Imports
import jakarta.mail.*;
import jakarta.mail.internet.*;


public class OTPGenerator extends JFrame {

    private static final long serialVersionUID = 1L;
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private JTextField emailField;
    private JTextField otpField;
    private String generatedOTP;
    private Timer otpTimer;
    private int timeLeft = 120;
    private Point initialClick;

    // Security & Credentials
    private String smtpEmail;
    private String smtpPassword;
    private boolean isMockMode = false;

    public OTPGenerator() {
        loadCredentials();
        setTitle("OTP Verification");
        setSize(420, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setUndecorated(true);
        setShape(new RoundRectangle2D.Double(0, 0, 420, 620, 30, 30));

        setLayout(new BorderLayout());
        add(createTitleBar(), BorderLayout.NORTH);
        
        add(mainPanel, BorderLayout.CENTER);
        mainPanel.add(emailPage(), "EMAIL");
        mainPanel.add(otpPage(), "OTP");

        cardLayout.show(mainPanel, "EMAIL");
        setVisible(true);
    }

    private void loadCredentials() {
        smtpEmail = System.getenv("SMTP_EMAIL");
        smtpPassword = System.getenv("SMTP_PASSWORD");

        if (smtpEmail == null || smtpPassword == null) {
            try (FileInputStream fis = new FileInputStream("config.properties")) {
                Properties props = new Properties();
                props.load(fis);
                smtpEmail = props.getProperty("SMTP_EMAIL");
                smtpPassword = props.getProperty("SMTP_PASSWORD");
            } catch (IOException e) {
                // Ignore, will fallback to mock mode
            }
        }

        if (smtpEmail == null || smtpEmail.trim().isEmpty() || smtpPassword == null || smtpPassword.trim().isEmpty()) {
            isMockMode = true;
            System.out.println("No SMTP credentials found. Running in UI/Mock Demo Mode.");
        }
    }

    private JPanel createTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(255, 90, 160)); 
        bar.setPreferredSize(new Dimension(420, 40));

        bar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { initialClick = e.getPoint(); }
        });
        bar.addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                int thisX = getLocation().x;
                int thisY = getLocation().y;
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                setLocation(thisX + xMoved, thisY + yMoved);
            }
        });

        JLabel title = new JLabel("  Secure OTP Generator");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        bar.add(title, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnPanel.setOpaque(false);

        btnPanel.add(createNavBtn("—", e -> setState(Frame.ICONIFIED)));
        btnPanel.add(createNavBtn("▢", e -> {
            if (getExtendedState() == Frame.MAXIMIZED_BOTH) setExtendedState(Frame.NORMAL);
            else setExtendedState(Frame.MAXIMIZED_BOTH);
        }));
        btnPanel.add(createNavBtn("✕", e -> System.exit(0)));

        bar.add(btnPanel, BorderLayout.EAST);
        return bar;
    }

    private JButton createNavBtn(String symbol, ActionListener action) {
        JButton b = new JButton(symbol);
        b.setFont(new Font("SansSerif", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addActionListener(action);
        return b;
    }

    /* ===================== PAGES ===================== */
    private JPanel emailPage() {
        JPanel bg = gradientBackground();
        bg.setLayout(new GridBagLayout());
        JPanel card = glassCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel title = title("Verify your email");
        JLabel sub = subtitle("We’ll send you a 6-digit OTP");
        emailField = modernField("Enter email address");
        
        AnimatedGlassButton btn = new AnimatedGlassButton("Generate OTP");
        btn.addActionListener(e -> {
            String email = emailField.getText().trim();
            btn.setLoading(true);
            new Thread(() -> sendOTP(btn, email)).start();
        });

        card.add(title);
        card.add(Box.createVerticalStrut(8));
        card.add(sub);
        card.add(Box.createVerticalStrut(30));
        card.add(emailField);
        card.add(Box.createVerticalStrut(40));
        card.add(btn);

        bg.add(card);
        return bg;
    }

    private JPanel otpPage() {
        JPanel bg = gradientBackground();
        bg.setLayout(new GridBagLayout());
        JPanel card = glassCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel title = title("Enter OTP");
        JLabel timerLabel = subtitle("02:00");
        otpField = modernField("Enter 6-digit OTP");
        
        AnimatedGlassButton verify = new AnimatedGlassButton("Verify OTP");
        AnimatedGlassButton resend = new AnimatedGlassButton("Resend OTP");

        verify.addActionListener(e -> verifyOTP());
        resend.addActionListener(e -> {
            String email = emailField.getText().trim();
            resend.setLoading(true);
            new Thread(() -> resendOTP(resend, timerLabel, email)).start();
        });
        startTimer(timerLabel);

        card.add(title);
        card.add(Box.createVerticalStrut(10));
        card.add(timerLabel);
        card.add(Box.createVerticalStrut(30));
        card.add(otpField);
        card.add(Box.createVerticalStrut(30));
        card.add(verify);
        card.add(Box.createVerticalStrut(12));
        card.add(resend);

        bg.add(card);
        return bg;
    }

    /* ===================== LOGIC ===================== */
    
    private boolean executeSMTPSend(String recipient, String otp) {
        if (isMockMode) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {} // Simulate network delay
            return true;
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        
        // This ensures Angus Activation is visible to the Mail session
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpEmail, smtpPassword);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject("Your OTP Verification Code");
            
            // The content handler (Angus Activation) now manages this without crashing
            message.setText("Hello!\n\nYour 6-digit verification code is: " + otp);

            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void sendOTP(AnimatedGlassButton btn, String email) {
        if (!Pattern.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$", email)) {
            SwingUtilities.invokeLater(() -> {
                AnimatedAlert.show(this, "Whoops!", "Invalid email format ");
                btn.setLoading(false);
            });
            return;
        }

        SecureRandom secureRandom = new SecureRandom();
        generatedOTP = String.valueOf(100000 + secureRandom.nextInt(900000));
        boolean success = executeSMTPSend(email, generatedOTP);

        SwingUtilities.invokeLater(() -> {
            btn.setLoading(false);
            if (success) {
                String title = isMockMode ? "Demo Mode 💌" : "OTP Sent 💌";
                String msg = isMockMode ? "Mock OTP (" + generatedOTP + ") sent to " + email : "Check your inbox!";
                AnimatedAlert.show(this, title, msg);
                cardLayout.show(mainPanel, "OTP");
            } else {
                AnimatedAlert.show(this, "Error", "Failed to send email. Check console.");
            }
        });
    }

    private void verifyOTP() {
        if (otpField.getText().equals(generatedOTP)) {
            AnimatedAlert.show(this, "Success ", "OTP verified successfully!");
        } else {
            AnimatedAlert.show(this, "Uh-oh!", "Invalid OTP. Try again 💕");
        }
    }

    private void resendOTP(AnimatedGlassButton btn, JLabel timerLabel, String email) {
        SecureRandom secureRandom = new SecureRandom();
        generatedOTP = String.valueOf(100000 + secureRandom.nextInt(900000));
        boolean success = executeSMTPSend(email, generatedOTP);

        SwingUtilities.invokeLater(() -> {
            btn.setLoading(false);
            if (success) {
                startTimer(timerLabel);
                String title = isMockMode ? "Demo Mode 💌" : "Resent 💌";
                String msg = isMockMode ? "Mock OTP (" + generatedOTP + ") resent to " + email : "A new OTP has been sent.";
                AnimatedAlert.show(this, title, msg);
            } else {
                AnimatedAlert.show(this, "Error", "Resend failed.");
            }
        });
    }

    private void startTimer(JLabel label) {
        timeLeft = 60;
        if (otpTimer != null) otpTimer.stop();
        otpTimer = new Timer(1000, e -> {
            timeLeft--;
            label.setText(String.format("%02d:%02d", timeLeft / 60, timeLeft % 60));
            if (timeLeft <= 0) {
                otpTimer.stop();
                AnimatedAlert.show(this, "Time’s up ⏰", "OTP expired. Please resend.");
            }
        });
        otpTimer.start();
    }

    /* ===================== UI HELPERS ===================== */
    private JPanel gradientBackground() {
        return new JPanel() {
            private static final long serialVersionUID = 1L;
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 214, 231), 0, getHeight(), new Color(255, 245, 250)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
    }

    private JPanel glassCard() {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(360, 440));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(30, 30, 30, 30));
        return p;
    }

    private JLabel title(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.BOLD, 26));
        l.setForeground(new Color(255, 90, 160));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JLabel subtitle(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.PLAIN, 14));
        l.setForeground(new Color(140, 80, 120));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JTextField modernField(String placeholder) {
        JTextField f = new JTextField(20);
        f.setMaximumSize(new Dimension(260, 45));
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 180, 210), 1),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        f.setText(placeholder);
        f.setForeground(Color.GRAY);
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { if (f.getText().equals(placeholder)) { f.setText(""); f.setForeground(Color.BLACK); }}
            public void focusLost(FocusEvent e) { if (f.getText().isEmpty()) { f.setText(placeholder); f.setForeground(Color.GRAY); }}
        });
        return f;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(OTPGenerator::new);
    }
}

/* ===================== ANIMATED BUTTON ===================== */
class AnimatedGlassButton extends JButton {
    private static final long serialVersionUID = 1L;
    private float alpha = 0.7f; 
    private boolean hover = false;
    private boolean loading = false;
    private int spinAngle = 0;
    private Timer fadeTimer;
    private Timer spinTimer;

    public AnimatedGlassButton(String text) {
        super(text);
        setFont(new Font("SansSerif", Font.BOLD, 14));
        setForeground(Color.WHITE);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setAlignmentX(Component.CENTER_ALIGNMENT);
        setMaximumSize(new Dimension(220, 50));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { hover = true; fade(1.0f); }
            public void mouseExited(MouseEvent e) { hover = false; fade(0.7f); }
        });

        spinTimer = new Timer(20, e -> {
            spinAngle = (spinAngle + 10) % 360;
            repaint();
        });
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        this.setEnabled(!loading);
        if (loading) spinTimer.start();
        else spinTimer.stop();
        repaint();
    }

    private void fade(float target) {
        if (fadeTimer != null) fadeTimer.stop();
        fadeTimer = new Timer(15, e -> {
            float diff = target - alpha;
            if (Math.abs(diff) < 0.05f) {
                alpha = target;
                ((Timer) e.getSource()).stop();
            } else {
                alpha += (diff > 0) ? 0.05f : -0.05f;
            }
            alpha = Math.max(0.0f, Math.min(1.0f, alpha));
            repaint();
        });
        fadeTimer.start();
    }

    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (hover && !loading) g2.translate(0, -2);

        GradientPaint gp = new GradientPaint(0, 0, new Color(255, 140, 190), 0, getHeight(), new Color(255, 80, 150));
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.0f, Math.min(1.0f, alpha))));
        
        if (hover && !loading) {
            g2.setColor(new Color(255, 90, 160, 100));
            g2.fillRoundRect(3, 3, getWidth()-6, getHeight()-3, 30, 30);
        }
        
        g2.setPaint(gp);
        g2.fillRoundRect(0, 0, getWidth(), getHeight()-5, 30, 30);

        if (loading) {
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3));
            g2.drawArc(getWidth()/2 - 10, getHeight()/2 - 13, 20, 20, spinAngle, 270);
        } else {
            super.paintComponent(g2);
        }
        g2.dispose();
    }
}

/* ===================== ANIMATED ALERT ===================== */
class AnimatedAlert extends JDialog {
    private static final long serialVersionUID = 1L;
    private float opacity = 0f;

    private AnimatedAlert(JFrame parent, String title, String message) {
        super(parent, true);
        setSize(320, 200);
        setLocationRelativeTo(parent);
        setUndecorated(true);
        setShape(new RoundRectangle2D.Double(0, 0, 320, 200, 25, 25));

        JPanel panel = new JPanel() {
            private static final long serialVersionUID = 1L;
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 220, 235), 0, getHeight(), Color.WHITE));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
            }
        };
        panel.setBorder(new EmptyBorder(25, 20, 20, 20));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(new Font("SansSerif", Font.BOLD, 20));
        t.setForeground(new Color(255, 70, 140));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel m = new JLabel("<html><div style='text-align: center; width: 220px;'>" + message + "</div></html>", SwingConstants.CENTER);
        m.setFont(new Font("SansSerif", Font.PLAIN, 14));
        m.setForeground(new Color(180, 70, 120)); 
        m.setAlignmentX(Component.CENTER_ALIGNMENT);

        AnimatedGlassButton ok = new AnimatedGlassButton("OK");
        ok.addActionListener(e -> dispose());

        panel.add(t);
        panel.add(Box.createVerticalStrut(15));
        panel.add(m);
        panel.add(Box.createVerticalGlue());
        panel.add(ok);
        panel.add(Box.createVerticalStrut(10));

        add(panel);
        fadeIn();
    }

    private void fadeIn() {
        Timer timer = new Timer(20, e -> {
            opacity += 0.1f;
            if (opacity >= 1f) { opacity = 1f; ((Timer) e.getSource()).stop(); }
            setOpacity(opacity);
        });
        timer.start();
    }

    public static void show(JFrame parent, String title, String message) {
        new AnimatedAlert(parent, title, message).setVisible(true);
    }
}

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.*;
import java.util.UUID;

/**
 * UrbanPulseBookingApp
 * 
 * Java Swing GUI for the "Book a Pitch" use case in the UrbanPulse Leisure
 * Centres information system. Implements BR-10 (Weekday tier restriction),
 * grace-period handling, membership validation, payment simulation, and
 * audit-trail output across a four-screen CardLayout flow.
 *
 * Use case covered: UC03 — Book Pitch
 * Actor:            Member (online self-service flow)
 * 
 * Compile:  javac UrbanPulseBookingApp.java
 * Run:      java  UrbanPulseBookingApp
 */
public class UrbanPulseBookingApp extends JFrame {

    // ── CardLayout panel names ──────────────────────────────────────────────
    private static final String SCREEN_MEMBER   = "MEMBER_LOOKUP";
    private static final String SCREEN_BOOKING  = "BOOKING_DETAILS";
    private static final String SCREEN_PAYMENT  = "PAYMENT";
    private static final String SCREEN_CONFIRM  = "CONFIRMATION";

    // ── Colour palette (UrbanPulse brand) ──────────────────────────────────
    private static final Color BRAND_DARK   = new Color(26,  26,  26);
    private static final Color BRAND_RED    = new Color(204,  0,   0);
    private static final Color BRAND_WHITE  = Color.WHITE;
    private static final Color BRAND_LIGHT  = new Color(245, 245, 245);
    private static final Color BRAND_BORDER = new Color(200, 200, 200);

    // ── Fonts ──────────────────────────────────────────────────────────────
    private static final Font FONT_HEADING  = new Font("SansSerif", Font.BOLD,  18);
    private static final Font FONT_LABEL    = new Font("SansSerif", Font.BOLD,  12);
    private static final Font FONT_FIELD    = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font FONT_SMALL    = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font FONT_MONO     = new Font("Monospaced", Font.BOLD, 13);

    // ── Shared state passed between screens ────────────────────────────────
    private String memberName       = "";
    private String membershipNumber = "";
    private String membershipTier   = "";
    private String memberStatus     = "";   // ACTIVE | GRACE | LAPSED

    private String selectedSite     = "";
    private String facilityType     = "";
    private String selectedPitch    = "";
    private String bookingDate      = "";
    private String timeSlot         = "";
    private String dayOfWeek        = "";   // derived from bookingDate

    private String paymentMethod    = "";
    private String paymentStatus    = "";
    private String bookingReference = "";
    private double bookingPrice     = 0.0;

    // ── CardLayout container ───────────────────────────────────────────────
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel     mainPanel  = new JPanel(cardLayout);

    // ── Per-screen field references ────────────────────────────────────────

    // Screen 1 – Member Lookup
    private JTextField  memberNameField;
    private JTextField  membershipNumberField;
    private JComboBox<String> tierCombo;
    private JComboBox<String> statusCombo;

    // Screen 2 – Booking Details
    private JComboBox<String> siteCombo;
    private JComboBox<String> facilityCombo;
    private JComboBox<String> pitchCombo;
    private JComboBox<String> dateCombo;
    private JComboBox<String> timeCombo;
    private JLabel            priceLabel;
    private JLabel            dayLabel;

    // Screen 3 – Payment
    private JComboBox<String> paymentMethodCombo;
    private JLabel            paymentAmountLabel;
    private JLabel            paymentSummaryLabel;
    private JRadioButton      payNowBtn;
    private JCheckBox         simulateFailureCheckbox;  // demo toggle for marker

    // Screen 4 – Confirmation
    private JTextArea confirmationArea;
    private JPanel    confirmSuccessBanner;   // swapped at runtime for grace-period
    private JPanel    confirmHeldBanner;
    private JLabel    confirmHeading;

    // ── Constructor ─────────────────────────────────────────────────────────
    public UrbanPulseBookingApp() {
        super("UrbanPulse Leisure Centres — Pitch Booking System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(640, 580);
        setLocationRelativeTo(null);
        setResizable(false);

        // Build all four screens and add them to the card panel
        mainPanel.add(buildMemberLookupScreen(), SCREEN_MEMBER);
        mainPanel.add(buildBookingDetailsScreen(), SCREEN_BOOKING);
        mainPanel.add(buildPaymentScreen(), SCREEN_PAYMENT);
        mainPanel.add(buildConfirmationScreen(), SCREEN_CONFIRM);

        add(buildTopBanner(), BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        cardLayout.show(mainPanel, SCREEN_MEMBER);
        setVisible(true);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SHARED LAYOUT HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /** Branded top banner shown on every screen. */
    private JPanel buildTopBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(BRAND_DARK);
        banner.setBorder(new EmptyBorder(10, 20, 10, 20));

        JLabel logo = new JLabel("URBANPULSE");
        logo.setFont(new Font("SansSerif", Font.BOLD, 22));
        logo.setForeground(BRAND_WHITE);

        JLabel sub = new JLabel("Leisure Centres  ·  Pitch Booking");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sub.setForeground(new Color(180, 180, 180));

        JPanel textBlock = new JPanel(new GridLayout(2, 1));
        textBlock.setBackground(BRAND_DARK);
        textBlock.add(logo);
        textBlock.add(sub);

        JLabel accent = new JLabel("● LIVE");
        accent.setFont(new Font("SansSerif", Font.BOLD, 11));
        accent.setForeground(new Color(80, 200, 80));
        accent.setHorizontalAlignment(SwingConstants.RIGHT);

        banner.add(textBlock, BorderLayout.WEST);
        banner.add(accent, BorderLayout.EAST);
        return banner;
    }

    /** Status bar at the foot of the window. */
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BRAND_DARK);
        bar.setBorder(new EmptyBorder(4, 16, 4, 16));

        JLabel left = new JLabel("UC03 — Book Pitch  |  Online Portal");
        left.setFont(FONT_SMALL);
        left.setForeground(new Color(160, 160, 160));

        JLabel right = new JLabel(
            LocalDate.now().format(DateTimeFormatter.ofPattern(
                "EEE dd MMM yyyy", java.util.Locale.UK)));
        right.setFont(FONT_SMALL);
        right.setForeground(new Color(160, 160, 160));

        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** Consistent section heading label with a red underline accent. */
    private JPanel sectionHeader(String title) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BRAND_WHITE);
        wrapper.setBorder(new EmptyBorder(0, 0, 8, 0));

        JLabel heading = new JLabel(title.toUpperCase());
        heading.setFont(FONT_HEADING);
        heading.setForeground(BRAND_DARK);

        JSeparator sep = new JSeparator();
        sep.setForeground(BRAND_RED);
        sep.setBackground(BRAND_RED);
        sep.setPreferredSize(new Dimension(0, 3));

        wrapper.add(heading, BorderLayout.NORTH);
        wrapper.add(sep, BorderLayout.SOUTH);
        return wrapper;
    }

    /** Standard label for form fields. */
    private JLabel fieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_LABEL);
        lbl.setForeground(BRAND_DARK);
        return lbl;
    }

    /** Styled text field. */
    private JTextField styledField(int cols) {
        JTextField tf = new JTextField(cols);
        tf.setFont(FONT_FIELD);
        tf.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BRAND_BORDER),
            new EmptyBorder(4, 6, 4, 6)));
        return tf;
    }

    /** Styled combo box. */
    private <T> JComboBox<T> styledCombo(T[] items) {
        JComboBox<T> cb = new JComboBox<>(items);
        cb.setFont(FONT_FIELD);
        cb.setBackground(BRAND_WHITE);
        return cb;
    }

    /** Primary action button (red). */
    private JButton primaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setBackground(BRAND_RED);
        btn.setForeground(BRAND_WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(160, 36));
        return btn;
    }

    /** Secondary (ghost) button. */
    private JButton secondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setBackground(BRAND_LIGHT);
        btn.setForeground(BRAND_DARK);
        btn.setFocusPainted(false);
        btn.setBorder(new LineBorder(BRAND_BORDER));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(120, 36));
        return btn;
    }

    /** Small info box with red left border — used for business-rule callouts. */
    private JPanel infoBox(String html) {
        JLabel lbl = new JLabel("<html>" + html + "</html>");
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(new Color(60, 60, 60));

        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(new Color(255, 245, 245));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, BRAND_RED),
            new EmptyBorder(8, 10, 8, 10)));
        box.add(lbl, BorderLayout.CENTER);
        return box;
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCREEN 1 — MEMBER LOOKUP
    // ════════════════════════════════════════════════════════════════════════

    private JPanel buildMemberLookupScreen() {
        JPanel screen = new JPanel(new BorderLayout(0, 12));
        screen.setBackground(BRAND_WHITE);
        screen.setBorder(new EmptyBorder(20, 30, 20, 30));

        screen.add(sectionHeader("Step 1 of 4  ·  Member Verification"), BorderLayout.NORTH);

        // Form grid
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BRAND_WHITE);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 12);
        c.anchor = GridBagConstraints.WEST;

        // Member name
        c.gridx = 0; c.gridy = 0; c.fill = GridBagConstraints.NONE;
        form.add(fieldLabel("Member Name *"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        memberNameField = styledField(20);
        memberNameField.setToolTipText("Enter the full name as registered");
        form.add(memberNameField, c);

        // Membership number
        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(fieldLabel("Membership Number *"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        membershipNumberField = styledField(20);
        membershipNumberField.setToolTipText("Format: M10001");
        form.add(membershipNumberField, c);

        // Membership tier
        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(fieldLabel("Membership Tier *"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        tierCombo = styledCombo(new String[]{
            "-- Select Tier --", "Junior", "Senior", "Weekday", "Full"});
        form.add(tierCombo, c);

        // Membership status
        c.gridx = 0; c.gridy = 3; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(fieldLabel("Membership Status *"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        statusCombo = styledCombo(new String[]{
            "Active", "Grace Period", "Lapsed"});
        form.add(statusCombo, c);

        // Business-rule callout
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(12, 4, 4, 4);
        form.add(infoBox(
            "<b>Membership rules:</b><br>" +
            "• <b>Active</b> — booking proceeds normally.<br>" +
            "• <b>Grace Period</b> — member may attend but renewal must be settled before" +
            " any booking is confirmed (BR-11).<br>" +
            "• <b>Lapsed</b> — booking and entry are refused until renewal is paid."), c);

        screen.add(form, BorderLayout.CENTER);

        // Navigation buttons
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        nav.setBackground(BRAND_WHITE);

        JButton nextBtn = primaryButton("Continue  →");
        nextBtn.addActionListener(e -> validateMemberAndAdvance());
        nav.add(nextBtn);

        screen.add(nav, BorderLayout.SOUTH);
        return screen;
    }

    /** Validates Screen 1 and moves to Screen 2. */
    private void validateMemberAndAdvance() {
        String name   = memberNameField.getText().trim();
        String number = membershipNumberField.getText().trim();
        String tier   = (String) tierCombo.getSelectedItem();
        String status = (String) statusCombo.getSelectedItem();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Member name is required.", "Validation Error",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (number.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Membership number is required.", "Validation Error",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (tier == null || tier.startsWith("--")) {
            JOptionPane.showMessageDialog(this,
                "Please select a membership tier.", "Validation Error",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        if ("Lapsed".equals(status)) {
            JOptionPane.showMessageDialog(this,
                "Member " + number + " has a lapsed membership.\n" +
                "Renewal must be paid before any booking can be made.\n\n" +
                "Please direct the member to the Renew Membership process.",
                "Booking Refused — Lapsed Membership",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        if ("Grace Period".equals(status)) {
            int choice = JOptionPane.showConfirmDialog(this,
                "Member " + number + " is within the 30-day grace period.\n" +
                "Renewal is overdue. The booking will be held pending renewal.\n\n" +
                "Do you wish to proceed?",
                "Grace Period Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        // Commit state
        memberName       = name;
        membershipNumber = number;
        membershipTier   = tier;
        memberStatus     = status;

        cardLayout.show(mainPanel, SCREEN_BOOKING);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCREEN 2 — BOOKING DETAILS
    // ════════════════════════════════════════════════════════════════════════

    private JPanel buildBookingDetailsScreen() {
        JPanel screen = new JPanel(new BorderLayout(0, 12));
        screen.setBackground(BRAND_WHITE);
        screen.setBorder(new EmptyBorder(20, 30, 20, 30));

        screen.add(sectionHeader("Step 2 of 4  ·  Booking Details"), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BRAND_WHITE);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 12);
        c.anchor = GridBagConstraints.WEST;

        // Site selection
        c.gridx = 0; c.gridy = 0; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(fieldLabel("Site *"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        siteCombo = styledCombo(new String[]{
            "UrbanPulse Central", "UrbanPulse North", "UrbanPulse East"});
        form.add(siteCombo, c);

        // Facility type
        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(fieldLabel("Facility Type *"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        facilityCombo = styledCombo(new String[]{
            "5-a-side Pitch", "7-a-side Pitch", "Tennis Court",
            "Badminton Court", "Swimming Pool Lane"});
        facilityCombo.addActionListener(e -> updatePitchOptions());
        form.add(facilityCombo, c);

        // Pitch
        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(fieldLabel("Pitch / Court *"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        pitchCombo = styledCombo(new String[]{"Pitch A", "Pitch B", "Pitch C"});
        form.add(pitchCombo, c);

        // Date
        c.gridx = 0; c.gridy = 3; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(fieldLabel("Booking Date *"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        dateCombo = styledCombo(buildDateOptions());
        dayLabel = new JLabel();
        dayLabel.setFont(FONT_SMALL);
        dayLabel.setForeground(new Color(100, 100, 100));
        dateCombo.addActionListener(e -> refreshDayLabel());
        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dateRow.setBackground(BRAND_WHITE);
        dateRow.add(dateCombo);
        dateRow.add(Box.createHorizontalStrut(10));
        dateRow.add(dayLabel);
        form.add(dateRow, c);

        // Time slot
        c.gridx = 0; c.gridy = 4; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(fieldLabel("Time Slot *"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        timeCombo = styledCombo(new String[]{
            "07:00 – 08:00  (Off-Peak)",
            "09:00 – 10:00  (Off-Peak)",
            "12:00 – 13:00  (Peak)",
            "13:00 – 14:00  (Peak)",
            "17:00 – 18:00  (Peak)",
            "18:00 – 19:00  (Peak)",
            "20:00 – 21:00  (Off-Peak)"});
        timeCombo.addActionListener(e -> updatePrice());
        form.add(timeCombo, c);

        // Estimated price
        c.gridx = 0; c.gridy = 5; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(fieldLabel("Estimated Price"), c);
        c.gridx = 1;
        priceLabel = new JLabel("£0.00");
        priceLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        priceLabel.setForeground(BRAND_RED);
        form.add(priceLabel, c);

        // Weekday-tier callout
        c.gridx = 0; c.gridy = 6; c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(12, 4, 4, 4);
        form.add(infoBox(
            "<b>BR-10 — Weekday Tier Restriction:</b> Weekday members may only book " +
            "Monday–Friday slots. Weekend selections will be rejected at validation."), c);

        screen.add(form, BorderLayout.CENTER);

        // Navigation
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        nav.setBackground(BRAND_WHITE);

        JButton backBtn = secondaryButton("←  Back");
        backBtn.addActionListener(e -> cardLayout.show(mainPanel, SCREEN_MEMBER));

        JButton nextBtn = primaryButton("Continue  →");
        nextBtn.addActionListener(e -> validateBookingAndAdvance());

        nav.add(backBtn);
        nav.add(nextBtn);
        screen.add(nav, BorderLayout.SOUTH);

        // Set initial state
        SwingUtilities.invokeLater(() -> {
            updatePitchOptions();
            refreshDayLabel();
            updatePrice();
        });

        return screen;
    }

    /** Populates pitch names according to the selected facility type. */
    private void updatePitchOptions() {
        if (pitchCombo == null || facilityCombo == null) return;
        String facility = (String) facilityCombo.getSelectedItem();
        pitchCombo.removeAllItems();
        if (facility != null && facility.contains("Tennis")) {
            pitchCombo.addItem("Court 1");
            pitchCombo.addItem("Court 2");
        } else if (facility != null && facility.contains("Badminton")) {
            pitchCombo.addItem("Court A");
            pitchCombo.addItem("Court B");
            pitchCombo.addItem("Court C");
        } else if (facility != null && facility.contains("Pool")) {
            pitchCombo.addItem("Lane 1");
            pitchCombo.addItem("Lane 2");
            pitchCombo.addItem("Lane 3");
        } else {
            pitchCombo.addItem("Pitch A");
            pitchCombo.addItem("Pitch B");
            pitchCombo.addItem("Pitch C");
        }
    }

    /** Updates the day-of-week label when the date combo changes. */
    private void refreshDayLabel() {
        if (dateCombo == null || dayLabel == null) return;
        String selected = (String) dateCombo.getSelectedItem();
        if (selected == null) return;
        try {
            LocalDate date = LocalDate.parse(
                selected, DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.UK));
            String dow = date.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.UK);
            dayLabel.setText("(" + dow + ")");
            boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY
                             || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            dayLabel.setForeground(isWeekend ? BRAND_RED : new Color(100, 100, 100));
        } catch (Exception ignored) {}
        updatePrice();
    }

    /** Calculates and displays a price based on facility type and peak/off-peak. */
    private void updatePrice() {
        if (timeCombo == null || priceLabel == null) return;
        String slot = (String) timeCombo.getSelectedItem();
        if (slot == null) return;
        boolean isPeak = slot.contains("Peak)") && !slot.contains("Off-Peak");
        String facility = facilityCombo != null
            ? (String) facilityCombo.getSelectedItem() : "";

        double base;
        if (facility != null && facility.contains("Pool"))      base = 4.50;
        else if (facility != null && facility.contains("7-a"))  base = 14.00;
        else if (facility != null && facility.contains("Badm")) base = 7.00;
        else if (facility != null && facility.contains("Tenn")) base = 8.50;
        else                                                     base = 10.00;

        bookingPrice = isPeak ? base * 1.35 : base;
        priceLabel.setText(String.format("£%.2f", bookingPrice));
    }

    /** Builds the next 14 days (excluding today) as formatted strings. */
    private String[] buildDateOptions() {
        String[] dates = new String[14];
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.UK);
        LocalDate start = LocalDate.now().plusDays(1);
        for (int i = 0; i < 14; i++) {
            dates[i] = start.plusDays(i).format(fmt);
        }
        return dates;
    }

    /** Validates Screen 2, enforces BR-10, and advances to payment. */
    private void validateBookingAndAdvance() {
        // Derive day-of-week from the selected date
        String rawDate = (String) dateCombo.getSelectedItem();
        LocalDate chosen;
        try {
            chosen = LocalDate.parse(
                rawDate, DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.UK));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Invalid date selection.", "Validation Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean isWeekend = chosen.getDayOfWeek() == DayOfWeek.SATURDAY
                         || chosen.getDayOfWeek() == DayOfWeek.SUNDAY;

        // BR-10 — Weekday tier restriction
        if ("Weekday".equals(membershipTier) && isWeekend) {
            JOptionPane.showMessageDialog(this,
                "BR-10 Violation: Weekday tier members may not book weekend slots.\n\n" +
                "Member: " + memberName + " (" + membershipNumber + ")\n" +
                "Tier:   Weekday\n" +
                "Date:   " + rawDate + " (" + chosen.getDayOfWeek() + ")\n\n" +
                "Please select a Monday–Friday date to proceed.",
                "Weekend Booking Refused", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Commit booking state
        selectedSite  = (String) siteCombo.getSelectedItem();
        facilityType  = (String) facilityCombo.getSelectedItem();
        selectedPitch = (String) pitchCombo.getSelectedItem();
        bookingDate   = rawDate;
        dayOfWeek     = chosen.getDayOfWeek()
            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.UK);
        timeSlot      = (String) timeCombo.getSelectedItem();

        updatePaymentScreen();
        cardLayout.show(mainPanel, SCREEN_PAYMENT);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCREEN 3 — PAYMENT
    // ════════════════════════════════════════════════════════════════════════

    private JPanel buildPaymentScreen() {
        JPanel screen = new JPanel(new BorderLayout(0, 12));
        screen.setBackground(BRAND_WHITE);
        screen.setBorder(new EmptyBorder(20, 30, 20, 30));

        screen.add(sectionHeader("Step 3 of 4  ·  Payment"), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BRAND_WHITE);

        // Booking summary card
        paymentSummaryLabel = new JLabel();
        paymentSummaryLabel.setFont(FONT_FIELD);
        JPanel summaryBox = new JPanel(new BorderLayout());
        summaryBox.setBackground(BRAND_LIGHT);
        summaryBox.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BRAND_BORDER),
            new EmptyBorder(10, 14, 10, 14)));
        summaryBox.add(paymentSummaryLabel, BorderLayout.CENTER);
        summaryBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        body.add(summaryBox);
        body.add(Box.createVerticalStrut(16));

        // Amount due
        JPanel amountRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        amountRow.setBackground(BRAND_WHITE);
        amountRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel amountLbl = new JLabel("Total Due:");
        amountLbl.setFont(FONT_LABEL);
        paymentAmountLabel = new JLabel("£0.00");
        paymentAmountLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        paymentAmountLabel.setForeground(BRAND_RED);
        amountRow.add(amountLbl);
        amountRow.add(paymentAmountLabel);
        body.add(amountRow);
        body.add(Box.createVerticalStrut(10));

        // Payment method (online self-service flow only)
        JPanel methodRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        methodRow.setBackground(BRAND_WHITE);
        methodRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        methodRow.add(fieldLabel("Payment Method:"));
        paymentMethodCombo = styledCombo(new String[]{
            "Credit / Debit Card", "Apple Pay", "Google Pay", "PayPal"});
        methodRow.add(paymentMethodCombo);
        body.add(methodRow);
        body.add(Box.createVerticalStrut(10));

        // Hidden radio kept for reference; online portal always pays immediately
        payNowBtn = new JRadioButton("Pay now — booking confirmed on successful payment", true);
        payNowBtn.setFont(FONT_FIELD);
        payNowBtn.setBackground(BRAND_WHITE);
        payNowBtn.setEnabled(false);   // fixed: online flow always pays now
        body.add(payNowBtn);
        body.add(Box.createVerticalStrut(16));

        // Marker demo toggle — deterministic, no random outcomes
        simulateFailureCheckbox = new JCheckBox(
            "Simulate payment failure (for demonstration purposes)");
        simulateFailureCheckbox.setFont(FONT_SMALL);
        simulateFailureCheckbox.setBackground(BRAND_WHITE);
        simulateFailureCheckbox.setForeground(new Color(120, 60, 0));
        body.add(simulateFailureCheckbox);
        body.add(Box.createVerticalStrut(16));

        // Cancellation policy callout
        body.add(infoBox(
            "<b>Cancellation policy (BR-12):</b> Cancellations made 24 hours or more " +
            "before the slot receive a full refund. Inside 24 hours, no refund is issued. " +
            "Staff may apply a discretionary refund and must record a reason."));

        screen.add(body, BorderLayout.CENTER);

        // Navigation
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        nav.setBackground(BRAND_WHITE);

        JButton backBtn = secondaryButton("←  Back");
        backBtn.addActionListener(e -> cardLayout.show(mainPanel, SCREEN_BOOKING));

        JButton payBtn = primaryButton("Confirm & Pay  →");
        payBtn.addActionListener(e -> processPayment());

        nav.add(backBtn);
        nav.add(payBtn);
        screen.add(nav, BorderLayout.SOUTH);
        return screen;
    }

    /** Refreshes the payment summary label from shared state. */
    private void updatePaymentScreen() {
        if (paymentSummaryLabel == null) return;
        paymentSummaryLabel.setText(
            "<html><b>" + memberName + "</b>  (" + membershipNumber + ")  —  "
            + membershipTier + " tier<br>"
            + selectedSite + "  ·  " + facilityType + "  ·  " + selectedPitch + "<br>"
            + bookingDate + " (" + dayOfWeek + ")  ·  " + timeSlot + "</html>");
        if (paymentAmountLabel != null) {
            paymentAmountLabel.setText(String.format("£%.2f", bookingPrice));
        }
    }

    /** Processes payment and advances to Screen 4. */
    private void processPayment() {
        paymentMethod = (String) paymentMethodCombo.getSelectedItem();

        // Payment outcome is controlled by the marker's checkbox — no random chance
        boolean paymentDeclined = simulateFailureCheckbox.isSelected();

        if (paymentDeclined) {
            JOptionPane.showMessageDialog(this,
                "Payment declined by the payment provider.\n\n" +
                "Please check the card details or try a different payment method.\n" +
                "The slot hold will be released automatically.",
                "Payment Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // BR-11: Grace-period members may not have a booking confirmed until renewal
        // is settled. The booking is created with status "Held — Renewal Required".
        boolean gracePeriod = "Grace Period".equals(memberStatus);
        paymentStatus    = gracePeriod ? "Held — Renewal Required" : "Paid";
        bookingReference = "UP-" + UUID.randomUUID()
            .toString().substring(0, 8).toUpperCase();

        buildConfirmationText();
        cardLayout.show(mainPanel, SCREEN_CONFIRM);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCREEN 4 — CONFIRMATION
    // ════════════════════════════════════════════════════════════════════════

    private JPanel buildConfirmationScreen() {
        JPanel screen = new JPanel(new BorderLayout(0, 12));
        screen.setBackground(BRAND_WHITE);
        screen.setBorder(new EmptyBorder(20, 30, 20, 30));

        // Heading — updated at runtime depending on grace-period state
        confirmHeading = new JLabel();
        confirmHeading.setFont(FONT_HEADING);
        confirmHeading.setForeground(BRAND_DARK);
        JSeparator sep = new JSeparator();
        sep.setForeground(BRAND_RED);
        sep.setBackground(BRAND_RED);
        sep.setPreferredSize(new Dimension(0, 3));
        JPanel headingPanel = new JPanel(new BorderLayout());
        headingPanel.setBackground(BRAND_WHITE);
        headingPanel.setBorder(new EmptyBorder(0, 0, 8, 0));
        headingPanel.add(confirmHeading, BorderLayout.NORTH);
        headingPanel.add(sep, BorderLayout.SOUTH);
        screen.add(headingPanel, BorderLayout.NORTH);

        // Success banner (green) — shown for fully confirmed bookings
        confirmSuccessBanner = new JPanel(new FlowLayout(FlowLayout.CENTER));
        confirmSuccessBanner.setBackground(new Color(230, 255, 235));
        confirmSuccessBanner.setBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(34, 139, 34)));
        JLabel successLbl = new JLabel("✔  Booking confirmed — payment received");
        successLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        successLbl.setForeground(new Color(34, 100, 34));
        confirmSuccessBanner.add(successLbl);

        // Held banner (amber) — shown when member is in grace period
        confirmHeldBanner = new JPanel(new FlowLayout(FlowLayout.CENTER));
        confirmHeldBanner.setBackground(new Color(255, 248, 220));
        confirmHeldBanner.setBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(200, 140, 0)));
        JLabel heldLbl = new JLabel(
            "⚠  Booking held — payment taken, but confirmation requires renewal");
        heldLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        heldLbl.setForeground(new Color(140, 90, 0));
        confirmHeldBanner.add(heldLbl);

        // Confirmation text area
        confirmationArea = new JTextArea(13, 50);
        confirmationArea.setFont(FONT_MONO);
        confirmationArea.setEditable(false);
        confirmationArea.setBackground(BRAND_LIGHT);
        confirmationArea.setBorder(new EmptyBorder(12, 14, 12, 14));

        JScrollPane scroll = new JScrollPane(confirmationArea);
        scroll.setBorder(new LineBorder(BRAND_BORDER));

        // A wrapper that will hold whichever banner is appropriate
        JPanel bannerSlot = new JPanel(new BorderLayout());
        bannerSlot.setName("bannerSlot");
        bannerSlot.setBackground(BRAND_WHITE);

        JPanel centre = new JPanel(new BorderLayout(0, 8));
        centre.setBackground(BRAND_WHITE);
        centre.add(bannerSlot, BorderLayout.NORTH);
        centre.add(scroll, BorderLayout.CENTER);
        screen.add(centre, BorderLayout.CENTER);

        // Navigation
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        nav.setBackground(BRAND_WHITE);

        JButton newBookingBtn = primaryButton("New Booking");
        newBookingBtn.addActionListener(e -> resetToStart());

        JButton emailBtn = secondaryButton("Email Copy");
        emailBtn.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "Confirmation sent to the member's registered email address.",
            "Email Sent", JOptionPane.INFORMATION_MESSAGE));

        nav.add(emailBtn);
        nav.add(newBookingBtn);
        screen.add(nav, BorderLayout.SOUTH);
        return screen;
    }

    /** Writes the receipt and wires the correct banner/heading for the member's state. */
    private void buildConfirmationText() {
        if (confirmationArea == null) return;

        boolean gracePeriod  = "Grace Period".equals(memberStatus);
        boolean heldBooking  = gracePeriod;  // BR-11: grace = held, not confirmed

        // ── Update heading text ───────────────────────────────────────────
        if (confirmHeading != null) {
            String headText = heldBooking
                ? "STEP 4 OF 4  ·  BOOKING HELD — RENEWAL REQUIRED"
                : "STEP 4 OF 4  ·  BOOKING CONFIRMED";
            confirmHeading.setText(headText);
        }

        // ── Swap banner panel ─────────────────────────────────────────────
        // Walk the component hierarchy to reach the named bannerSlot panel
        if (confirmSuccessBanner != null && confirmHeldBanner != null) {
            JPanel slot = findBannerSlot(mainPanel);
            if (slot != null) {
                slot.removeAll();
                slot.add(heldBooking ? confirmHeldBanner : confirmSuccessBanner,
                    BorderLayout.CENTER);
                slot.revalidate();
                slot.repaint();
            }
        }

        // ── Locale-safe date/time formatters ─────────────────────────────
        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern(
            "dd MMM yyyy HH:mm:ss", java.util.Locale.UK);
        DateTimeFormatter dFmt  = DateTimeFormatter.ofPattern(
            "dd MMM yyyy", java.util.Locale.UK);

        String now        = LocalDateTime.now().format(dtFmt);
        String renewBy    = LocalDate.now().plusDays(20).format(dFmt);
        String bookStatus = heldBooking ? "HELD — Renewal Required" : "CONFIRMED";

        // ── Build receipt text ────────────────────────────────────────────
        confirmationArea.setText(
            "════════════════════════════════════════════════\n" +
            "         URBANPULSE LEISURE CENTRES             \n" +
            "       PITCH BOOKING " + (heldBooking ? "HOLD NOTICE" : "CONFIRMATION") + "\n" +
            "════════════════════════════════════════════════\n" +
            "\n" +
            "  Reference  : " + bookingReference + "\n" +
            "  Issued      : " + now + "\n" +
            "  Status      : " + bookStatus + "\n" +
            "\n" +
            "  MEMBER DETAILS\n" +
            "  ─────────────────────────────────────────────\n" +
            "  Name        : " + memberName + "\n" +
            "  Number      : " + membershipNumber + "\n" +
            "  Tier        : " + membershipTier + "\n" +
            "  Standing    : " + memberStatus + "\n" +
            "\n" +
            "  BOOKING DETAILS\n" +
            "  ─────────────────────────────────────────────\n" +
            "  Site        : " + selectedSite + "\n" +
            "  Facility    : " + facilityType + "\n" +
            "  Pitch       : " + selectedPitch + "\n" +
            "  Date        : " + bookingDate + " (" + dayOfWeek + ")\n" +
            "  Time Slot   : " + timeSlot + "\n" +
            "\n" +
            "  PAYMENT\n" +
            "  ─────────────────────────────────────────────\n" +
            "  Method      : " + paymentMethod + "\n" +
            "  Amount      : " + String.format("£%.2f", bookingPrice) + "\n" +
            "  Status      : " + paymentStatus + "\n" +
            "\n" +
            "  CANCELLATION POLICY (BR-12)\n" +
            "  ─────────────────────────────────────────────\n" +
            "  ≥ 24 hours notice  →  full refund\n" +
            "  < 24 hours notice  →  no refund\n" +
            "\n" +
            (heldBooking ?
            "  ⚠  RENEWAL REQUIRED (BR-11)\n" +
            "  ─────────────────────────────────────────────\n" +
            "  Your membership is in the grace period.\n" +
            "  This booking cannot be confirmed until\n" +
            "  renewal is paid. Please renew by:\n" +
            "  " + renewBy + "\n" +
            "  After renewal, this hold converts to\n" +
            "  a confirmed booking automatically.\n\n"
            : "") +
            "════════════════════════════════════════════════\n" +
            "  A copy has been sent to your registered\n" +
            "  email address.\n" +
            "════════════════════════════════════════════════\n"
        );

        // Audit log — simulates AuditEvent domain class
        String auditType = heldBooking ? "BOOKING_HELD" : "BOOKING_CREATED";
        System.out.println("[AUDIT] " + now
            + "  |  " + auditType
            + "  |  ref="     + bookingReference
            + "  |  member="  + membershipNumber
            + "  |  pitch="   + selectedPitch
            + "  |  date="    + bookingDate
            + "  |  payment=" + paymentStatus);
    }

    /** Recursively finds the JPanel named "bannerSlot" within a container. */
    private JPanel findBannerSlot(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JPanel p) {
                if ("bannerSlot".equals(p.getName())) return p;
                JPanel found = findBannerSlot(p);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Clears all shared state and returns to Screen 1. */
    private void resetToStart() {
        memberNameField.setText("");
        membershipNumberField.setText("");
        tierCombo.setSelectedIndex(0);
        statusCombo.setSelectedIndex(0);
        memberName = membershipNumber = membershipTier = memberStatus = "";
        selectedSite = facilityType = selectedPitch = "";
        bookingDate = timeSlot = dayOfWeek = "";
        paymentMethod = paymentStatus = bookingReference = "";
        bookingPrice = 0.0;
        cardLayout.show(mainPanel, SCREEN_MEMBER);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ENTRY POINT
    // ════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        // Use the system look-and-feel for crisper rendering on the host OS
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(UrbanPulseBookingApp::new);
    }
}

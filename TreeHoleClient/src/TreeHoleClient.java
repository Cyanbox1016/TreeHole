/**
 * @author: Wang Yongqi 3180105481
 * Date: 2021/01/10
 * Client of the Treehole BBS system
 */
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Random;
import java.util.Vector;

/**
 * Store the message of exception.
 * Used by JOptionPane.showMessageDialog when exceptions happends
 */
class ErrMessage {
    static final String SQLException = "数据库异常；请检查数据库语法和数据库连接";
    static final String DriverError = "未找到MySQL驱动，请检查MySQL驱动路径是否正确";
    static final String SocketError = "Socket连接失败!";
    static final String SocketTransmissionError = "Socket传输失败，请检查连接";
    static final String fileIOException = "文件读写失败，请检查文件路径";
}

/**
 * Used for communication with MySQL
 */
class Sql {
    
    // path of the MySQL Driver
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    
    // path of the database
    static final String DB_URL = "jdbc:mysql://10.110.0.151:3306/treehole?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // username and password of the database
    static final String USER = "root";
    static final String PASS = "F0cus.0n";

    // tools to communacate with teh database
    static Connection conn = null;
    static Statement stmt = null;

    /**
     * Build connection with the database.
     * Called when the program starts.
     */
    static void connect() {
        try {
            Class.forName(JDBC_DRIVER);
            conn = DriverManager.getConnection(DB_URL,USER,PASS);
            stmt = conn.createStatement();
        } catch (SQLException se) {
            JOptionPane.showMessageDialog(null, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        } catch (ClassNotFoundException ce) {
            JOptionPane.showMessageDialog(null, ErrMessage.DriverError, "Driver Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    /**
     * Encapsulated select function of the database
     * 
     * @param req
     *        query statement of the request
     * @return the result set of the query
     */
    static ResultSet select (String req) {
        try {
            return stmt.executeQuery(req);
        } catch (SQLException se) {
            JOptionPane.showMessageDialog(null, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }

    /**
     * Encapsulated insert and update function of the database
     * @param req
     *        update statement of the request
     */
    static void insert (String req) {
        try {
            stmt.executeUpdate(req);
        } catch (SQLException se) {
            JOptionPane.showMessageDialog(null, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
        }
    }
}

/**
 * Encapsulated class to do communication with the file server
 */
class SocketConnector {
    
    // Socket info and IO streams
    private static String host = "127.0.0.1";
    private static int port = 5481;
    private static Socket socket;
    private static InputStream inputStream;
    private static OutputStream outputStream;
    private static BufferedReader bufferedReader;

    // String constants used in communication
    private static final String GET = "GET\n";
    private static final String POST = "POST\n";
    private static final String NEWPOST = "NEWPOST\n";
    private static final String COMMENT = "COMMENT\n";

    /**
     * Build socket connection with the server.
     * Called when the program starts.
     */
    static void connect() {
        try {
            socket = new Socket(host, port);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, ErrMessage.SocketError, "Socket Error", JOptionPane.ERROR_MESSAGE);
            System.exit(2);
        }
    }

    /**
     * Get the content of a post from the server
     * @param path
     *        the filepath of the post file on the server
     * @return A vector containing the contents
     * @throws IOException
     *         socket may fail during the transmission
     */
    static Vector getPostContent(String path) throws IOException {

        //  send request for post content
        outputStream.write(GET.getBytes(StandardCharsets.UTF_8));
        outputStream.write(path.getBytes(StandardCharsets.UTF_8));
        outputStream.write('\n');

        String readline;
        Vector content = new Vector();
        boolean flag = false;
        
        // read from the stream and get the content of the post
        while ((readline = bufferedReader.readLine()) != null) {
            if (readline.equals("$$START%%")) {
                flag = true;
            }
            else if (readline.equals("%%END$$")) {
                break;
            }
            else if (flag) {
                content.add(readline);
            }
        }
        return content;
    }

    /**
     * Send a new post to the server
     * @param contentToSend
     *        A vector containing the contents 
     * @throws IOException
     *         socket may fail during the transmission
     */
    static void post (Vector contentToSend) throws IOException {
        outputStream.write(POST.getBytes(StandardCharsets.UTF_8));
        outputStream.write(NEWPOST.getBytes(StandardCharsets.UTF_8));
        for (Object obj : contentToSend) {
            String str = (String) obj;
            str += "\n";
            outputStream.write(str.getBytes(StandardCharsets.UTF_8));
        }
        outputStream.write("%%END$$\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Send a comment to the server
     * @param contentToSend
     *        A vector containing the contents 
     * @throws IOException
     *         socket may fail during the transmission
     */
    static void comment (Vector contentToSend) throws IOException  {
        outputStream.write(POST.getBytes(StandardCharsets.UTF_8));
        outputStream.write(COMMENT.getBytes(StandardCharsets.UTF_8));
        for (Object obj : contentToSend) {
            String str = (String) obj;
            str += "\n";
            outputStream.write(str.getBytes(StandardCharsets.UTF_8));
        }
        outputStream.write("%%END$$\n".getBytes(StandardCharsets.UTF_8));
    }
}

/**
 * The main interface of the client
 */
class Gui extends JFrame {

    // Some elements in the GUI
    private JList postTitle;
    private DefaultListModel<Object> postTitleModel;
    private DefaultListModel<Object> postIDModel;

    private JComboBox<String> sections;

    private JLabel likeLabel;
    private JLabel dislikeLabel;

    private JTextPane postContentPane;
    private StyledDocument postContentDocument;

    private Random rand;
    private String idSelected;

    // Inner class of the posting interface
    class PostGui extends JFrame {

        public JTextField postTitleField;
        public JTextField postSenderField;
        public JTextArea postTextArea;

        public String sectionSelected;
        
        /**
         * Constructor of the PostGui
         * Create a new window of it
         */
        PostGui() {
            setSize(600, 400);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setTitle("发布帖子");
            setLocationRelativeTo(null);
            setVisible(true);

            JPanel postGuiPanel = new JPanel();
            postGuiPanel.setLayout(new BoxLayout(postGuiPanel, BoxLayout.Y_AXIS));
            postGuiPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel sectionPanel = new JPanel();
            sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.X_AXIS));

            JLabel sectionLabel = new JLabel("");
            sectionSelected = (String) sections.getSelectedItem();
            sectionLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            sectionLabel.setText("发帖版面：" + sectionSelected);
            sectionLabel.setAlignmentX(LEFT_ALIGNMENT);
            sectionLabel.setMinimumSize(new Dimension(3000, 20));

            sectionPanel.add(sectionLabel);

            JPanel postTitlePanel = new JPanel();
            postTitlePanel.setLayout(new BoxLayout(postTitlePanel, BoxLayout.X_AXIS));

            JLabel postTitleLable = new JLabel("主题");
            postTitleLable.setFont(new Font("微软雅黑", Font.PLAIN, 14));

            postTitleField = new JTextField();
            postTitleField.setMaximumSize(new Dimension(1200, 20));

            postTitlePanel.add(postTitleLable);
            postTitlePanel.add(Box.createRigidArea(new Dimension(10, 0)));
            postTitlePanel.add(postTitleField);

            JPanel postSenderPanel = new JPanel();
            postSenderPanel.setLayout(new BoxLayout(postSenderPanel, BoxLayout.X_AXIS));

            JLabel postSenderLabel = new JLabel("本次昵称");
            postSenderLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));

            postSenderField = new JTextField();
            postSenderField.setMaximumSize(new Dimension(1200, 20));

            postSenderPanel.add(postSenderLabel);
            postSenderPanel.add(Box.createRigidArea(new Dimension(10, 0)));
            postSenderPanel.add(postSenderField);

            postTextArea = new JTextArea();
            JScrollPane postTextPane = new JScrollPane(postTextArea);

            JButton sendButton = new JButton("发布帖子");
            sendButton.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            sendButton.setAlignmentX(CENTER_ALIGNMENT);
            sendButton.setBackground(new Color(0xF3F3F3));
            sendButton.addActionListener(new ActionListener() {
                
                /**
                 * Listeing function of the posting button
                 * Send the content to the server and insert info into the database.
                 * 
                 * @param e 
                 *        event of clicking
                 */
                @Override
                public void actionPerformed(ActionEvent e) {
                    String title = postTitleField.getText();
                    String nickname = postSenderField.getText();
                    String content = postTextArea.getText();
                    String contentHead = "楼主（" + nickname + "）：\n";
                    String id = null;
                    try {
                        do {
                            id = Integer.toHexString(rand.nextInt());
                        } while (Sql.select("SELECT * FROM posts WHERE id ='" + id + "'").next());
                    } catch (SQLException se) {
                        JOptionPane.showMessageDialog(PostGui.this, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
                    }

                    Vector contentToSend = new Vector();
                    contentToSend.add(id);
                    contentToSend.add(contentHead);
                    contentToSend.add(content);

                    try {
                        SocketConnector.post(contentToSend);
                        String req = "INSERT INTO posts VALUES ('" + title +  "','" + sectionSelected +
                                     "','" + nickname + "','" + "res/" + id + ".txt" + "'," + "0, 0, '" + id + "')";
                        Sql.insert(req);
                        FlushListContent(sectionSelected);
                    } catch (IOException ioException) {
                        JOptionPane.showMessageDialog(PostGui.this, ErrMessage.SocketTransmissionError, "Socket failed", JOptionPane.ERROR_MESSAGE);
                    }

                    dispose();
                }
            });

            postGuiPanel.add(sectionPanel);
            postGuiPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            postGuiPanel.add(postTitlePanel);
            postGuiPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            postGuiPanel.add(postSenderPanel);
            postGuiPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            postGuiPanel.add(postTextPane);
            postGuiPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            postGuiPanel.add(sendButton);

            add(postGuiPanel);
        }
    }

    /**
     * Construction of Gui
     * Create a new window of the main interface
     */
    Gui() {
        rand = new Random();
        setSize(900, 650);

        setLayout(new BorderLayout(10, 5));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("树洞BBS");
        setLocationRelativeTo(null);

        // section part
        JPanel sectionPanel = new JPanel();
        sectionPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        sectionPanel.setLayout(new BorderLayout(10, 10));

        JLabel sectionLabel = new JLabel();
        sectionLabel.setText("当前版面");
        sectionLabel.setFont(new Font("微软雅黑", Font.PLAIN, 16));
        sectionPanel.add(sectionLabel, BorderLayout.WEST);

        sections = new JComboBox<>();
        ResultSet sectionContent = Sql.select("SELECT name FROM section");
        try {
            while (sectionContent.next()) {
                sections.addItem(sectionContent.getString("name"));
            }
            sectionContent.close();
        } catch (SQLException se) {
            JOptionPane.showMessageDialog(Gui.this, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
        }
        sections.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        sections.setPreferredSize(new Dimension(60, 20));
        sections.addItemListener(new ItemListener() {
            /**
             * Listener of the section list.
             * Refresh the post list.
             * @param e
             *        event of value changing
             */
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    FlushListContent((String)e.getItem());
                }
            }
        });

        sectionPanel.add(sections, BorderLayout.CENTER);
        add(sectionPanel, BorderLayout.NORTH);

        // posts part

        postTitleModel = new DefaultListModel<>();
        postTitle = new JList(postTitleModel);
        postIDModel = new DefaultListModel<>();
        FlushListContent((String)sections.getSelectedItem());
        postContentDocument = new DefaultStyledDocument();

        postTitle.addListSelectionListener(new ListSelectionListener() {
            /**
             * Listener of the post list
             * Show the content of the post when a post is selected
             * @param e
             *        event of item selection
             */
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    int index = postTitle.getSelectedIndex();
                    String postID = (String) postIDModel.getElementAt(index);
                    flushLikeNumber(postID);
                    idSelected = postID;
                    String req = "SELECT filepath FROM posts WHERE id='" + postID + "'";
                    ResultSet rs = Sql.select(req);

                    postContentPane.setText("");

                    String filepath;
                    if (rs != null) {
                        try {
                            if (rs.next()) {
                                filepath = rs.getString("filepath");
                                Vector postContentVector = SocketConnector.getPostContent(filepath);
                                for (Object obj : postContentVector) {
                                    postContentPane.setText(postContentPane.getText() + (String) obj + "\n");
                                }
                            }
                        } catch (SQLException throwable) {
                            JOptionPane.showMessageDialog(Gui.this, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
                        } catch (IOException t) {
                            JOptionPane.showMessageDialog(Gui.this, ErrMessage.SocketTransmissionError, "Socket Failed", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });

        JScrollPane postPane = new JScrollPane(postTitle);

        JPanel postPanel = new JPanel();
        postPanel.setBorder(new EmptyBorder(5, 10, 10, 5));
        postPanel.setLayout(new BorderLayout(10, 10));
        postPanel.setPreferredSize(new Dimension(320, 500));

        JLabel postLabel = new JLabel();
        postLabel.setFont(new Font("微软雅黑", Font.PLAIN, 16));
        postLabel.setText("帖子列表");

        postPanel.add(postLabel, BorderLayout.NORTH);
        postPanel.setVisible(true);
        postPanel.add(postPane, BorderLayout.CENTER);

        JButton postButton = new JButton("我要发贴");
        postButton.setBackground(new Color(0xf3f3f3));
        postPanel.add(postButton, BorderLayout.SOUTH);
        postButton.setFont(new Font("微软雅黑", Font.PLAIN, 14));

        postButton.addActionListener(new ActionListener() {
            /**
             * Listening function of the add-a-post button
             * Create a PostGui window
             * 
             * @param e
             *        event of the action
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                PostGui postGui = new PostGui();
            }
        });

        add(postPanel, BorderLayout.WEST);

        // browser part
        JPanel browserPanel = new JPanel();
        browserPanel.setBorder(new EmptyBorder(5, 0, 10, 10));
        browserPanel.setLayout(new BorderLayout(10, 10));

        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        contentPanel.setLayout(new BorderLayout(10, 10));

        JLabel contentLabel = new JLabel();
        contentLabel.setFont(new Font("微软雅黑", Font.PLAIN, 16));
        contentLabel.setText("帖子内容");

        contentPanel.add(contentLabel, BorderLayout.NORTH);

        JPanel likeDislikePanel = new JPanel();
        likeLabel = new JLabel("0  ");
        likeLabel.setIcon(new ImageIcon("res\\like.png"));
        likeLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        likeDislikePanel.add(likeLabel);

        dislikeLabel = new JLabel("0  ");
        dislikeLabel.setIcon(new ImageIcon("res\\dislike.png"));
        dislikeLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        likeDislikePanel.add(dislikeLabel);
        likeDislikePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        likeDislikePanel.setPreferredSize(new Dimension(10, 20));

        contentPanel.add(likeDislikePanel, BorderLayout.SOUTH);

        postContentPane = new JTextPane();
        postContentPane.setPreferredSize(new Dimension(200, 300));
        postContentPane.setEditable(false);

        JScrollPane contentScrollPane = new JScrollPane(postContentPane);

        contentPanel.add(contentScrollPane, BorderLayout.CENTER);

        JTextPane commentPane = new JTextPane();
        commentPane.setPreferredSize(new Dimension(200, 150));
        commentPane.setEditable(true);

        JScrollPane commentScrollPane = new JScrollPane(commentPane);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 3, 5, 5));

        JButton like = new JButton("点赞");
        like.setBackground(new Color(0xF3F3F3));
        like.setIcon(new ImageIcon("res\\like.png"));
        like.addActionListener(new ActionListener() {
            /**
             * Listening function of the like button
             * add the like number of the present post
             * 
             * @param e
             *        event of the clicking
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                Sql.insert("UPDATE posts SET likenum = likenum + 1 WHERE id = '" + idSelected + "'");
                flushLikeNumber(idSelected);

                postContentPane.setText("");

                String req = "SELECT filepath FROM posts WHERE id='" + idSelected + "'";
                ResultSet rs = Sql.select(req);
                String filepath;
                if (rs != null) {
                    try {
                        if (rs.next()) {
                            filepath = rs.getString("filepath");
                            Vector postContentVector = SocketConnector.getPostContent(filepath);
                            for (Object obj : postContentVector) {
                                postContentPane.setText(postContentPane.getText() + (String) obj + "\n");
                            }
                        }
                    } catch (SQLException throwable) {
                        JOptionPane.showMessageDialog(Gui.this, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
                    } catch (IOException throwable) {
                        JOptionPane.showMessageDialog(Gui.this, ErrMessage.SocketTransmissionError, "Socket Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        JButton dislike = new JButton("点踩");
        dislike.setBackground(new Color(0xf3f3f3));
        dislike.setIcon(new ImageIcon("res\\dislike.png"));
        dislike.addActionListener(new ActionListener() {
            /**
             * Listening function of the dislike button
             * add the dislike number of the present post
             * 
             * @param e
             *        event of the clicking
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                Sql.insert("UPDATE posts SET dislike = dislike + 1 WHERE id = '" + idSelected + "'");
                flushLikeNumber(idSelected);
                postContentPane.setText("");

                String req = "SELECT filepath FROM posts WHERE id='" + idSelected + "'";
                ResultSet rs = Sql.select(req);
                String filepath;
                if (rs != null) {
                    try {
                        if (rs.next()) {
                            filepath = rs.getString("filepath");
                            Vector postContentVector = SocketConnector.getPostContent(filepath);
                            for (Object obj : postContentVector) {
                                postContentPane.setText(postContentPane.getText() + (String) obj + "\n");
                            }
                        }
                    } catch (SQLException throwable) {
                        JOptionPane.showMessageDialog(Gui.this, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
                    } catch (IOException throwable) {
                        JOptionPane.showMessageDialog(Gui.this, ErrMessage.SocketTransmissionError, "Socket Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        JButton doComment = new JButton("发表回复");
        doComment.setBackground(new Color(0xf3f3f3));
        doComment.addActionListener(new ActionListener() {
            /**
             * Listening function of the comment button
             * send a new comment of the present post
             * 
             * @param e
             *        event of the clicking
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                String id = idSelected;
                String commentContent = commentPane.getText();

                Vector contentToSend = new Vector();
                contentToSend.add(id);
                contentToSend.add("\n--------------------------------------------------------------------------------------\n");
                contentToSend.add("用户回复：\n");
                contentToSend.add(commentContent);

                try {
                    SocketConnector.comment(contentToSend);
                } catch (IOException ioException) {
                    JOptionPane.showMessageDialog(Gui.this, ErrMessage.SocketTransmissionError, "Socket Failed", JOptionPane.ERROR_MESSAGE);
                }

                commentPane.setText("");
                postContentPane.setText("");

                String req = "SELECT filepath FROM posts WHERE id='" + idSelected + "'";
                ResultSet rs = Sql.select(req);
                String filepath;
                if (rs != null) {
                    try {
                        if (rs.next()) {
                            filepath = rs.getString("filepath");
                            Vector postContentVector = SocketConnector.getPostContent(filepath);
                            for (Object obj : postContentVector) {
                                postContentPane.setText(postContentPane.getText() + (String) obj + "\n");
                            }
                        }
                    } catch (SQLException throwable) {
                        JOptionPane.showMessageDialog(Gui.this, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
                    } catch (IOException throwable) {
                        JOptionPane.showMessageDialog(Gui.this, ErrMessage.SocketTransmissionError, "Socket Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        like.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        dislike.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        doComment.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        buttonPanel.add(like);
        buttonPanel.add(dislike);
        buttonPanel.add(doComment);

        JPanel commentPanel = new JPanel();
        commentPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        commentPanel.setLayout(new BorderLayout(10, 10));
        commentPanel.add(commentScrollPane, BorderLayout.CENTER);
        commentPanel.add(buttonPanel, BorderLayout.SOUTH);

        browserPanel.add(contentPanel, BorderLayout.NORTH);
        browserPanel.add(commentPanel, BorderLayout.CENTER);

        add(browserPanel, BorderLayout.CENTER);

        setVisible(true);
    }
    
    /**
     * Refresh the post list when the section changes
     * @param section
     *        the section selected
     */
    private void FlushListContent(String section) {
        ResultSet postList = Sql.select("SELECT id, title FROM posts WHERE section='" + section + "'");
        postTitleModel.removeAllElements();
        postIDModel.removeAllElements();
        try {
            while (true) {
                assert postList != null;
                if (!postList.next()) break;
                postTitleModel.addElement(postList.getString("title"));
                postIDModel.addElement(postList.getString("id"));
            }
            postTitle.setModel(postTitleModel);
            postTitle.updateUI();
            postList.close();
        } catch (SQLException se) {
            JOptionPane.showMessageDialog(Gui.this, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Refresh the like and dislike number of the post
     * @param id
     *        the number of the post selected
     */
    private void flushLikeNumber(String id) {
        ResultSet rs = Sql.select("SELECT likenum, dislike FROM POSTS WHERE id = '" + id + "'");
        try {
            if (rs.next()) {
                String likenum = rs.getString("likenum");
                String dislike = rs.getString("dislike");
                likeLabel.setText(likenum);
                dislikeLabel.setText(dislike);
            }
        } catch (SQLException se) {
            JOptionPane.showMessageDialog(Gui.this, ErrMessage.SQLException, "SQL Exception", JOptionPane.ERROR_MESSAGE);
        }
    }
}

// Run the program
public class TreeHoleClient {
    public static void main(String[] args) {
        Sql.connect();
        SocketConnector.connect();
        Gui gui = new Gui();
    }
}

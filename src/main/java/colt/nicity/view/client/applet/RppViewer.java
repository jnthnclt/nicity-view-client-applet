/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package colt.nicity.view.client.applet;

import colt.nicity.core.collection.CArray;
import colt.nicity.core.lang.ICallback;
import colt.nicity.core.lang.IOut;
import colt.nicity.core.lang.SysOut;
import colt.nicity.core.lang.UBase64;
import colt.nicity.core.lang.URandom;
import colt.nicity.core.process.IAsyncResponse;
import colt.nicity.json.client.IJSONService;
import colt.nicity.json.client.JSONServices;
import colt.nicity.json.core.Jo;
import colt.nicity.json.core.UJson;
import colt.nicity.view.canvas.FilerCanvas;
import colt.nicity.view.canvas.GlueAWTGraphicsToCanvas;
import colt.nicity.view.event.ADK;
import colt.nicity.view.rpp.RPPFiler;
import java.applet.Applet;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.LinkedList;
import javax.swing.JFrame;


/*<applet code=RPPViewer.class width=800 height=800>
</applet>
 */
/**
 *
 * @author jonathan
 */
public class RppViewer extends Applet implements KeyListener, MouseListener, MouseMotionListener {

    long who = URandom.rand(Integer.MAX_VALUE);
    static IOut _ = new SysOut();
    //String address = "http://www.jonathancolt.com/";
    static String address = "http://localhost:8080/";
    static IJSONService eventsService = JSONServices.service("User", "Password", address + "nicity-view-server/rppevents");
    static IJSONService paintService = JSONServices.service("User", "Password", address + "nicity-view-server/rppview");
    Image buffer;
    Thread painterThread;
    RefreshStack pendingPaints = new RefreshStack();
    RefreshStack pendingEvents = new RefreshStack();
    CArray updates = new CArray();
    boolean stopThreads = false;
    String message = "Idle";
    
    public static void main(String[] _args) {

        final JFrame window = new JFrame("RPP");
        RppViewer theApplet = new RppViewer() {

            @Override
            public void changeSize(int width, int height) {
                super.changeSize(width, height);
                window.setSize(width+18, height+50);
                //window.doLayout();
            }
            
        };
        theApplet.init();         
        theApplet.start();       

        window.setContentPane(theApplet);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.pack();            
        window.setVisible(true);
    }

    public void changeSize(int w,int h) {
        setSize(w,h);
    }

    @Override
    public void init() {
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        changeSize(400, 400);
        setVisible(true);
        setBackground(Color.black);
    }

    @Override
    public void update(Graphics g) {
        if (updates.getCount() > 0) {
            while(updates.getCount() > 0) {
                Update u = (Update) updates.removeFirst();
                if (u != null) {
                    u.renderToBuffer();
                    u.rendertTo(g);
                }
            }
        } else {
            if (buffer != null) {
                g.drawImage(buffer, 0, 0, this);
            }
        }
        g.setColor(Color.orange);
        g.drawString(message, 10, getHeight() - 10);
        if (updates.getCount() > 0) {
            repaint();
        }
    }

    @Override
    public void paint(Graphics g) {
        update(g);
    }

    @Override
    public void start() {
        super.start();
        painterThread = new Thread() {
            @Override
            public void run() {
                while (!stopThreads) {
                    painter(who);
                }
            }
        };
        painterThread.start();
    }

    @Override
    public void stop() {
        super.stop();
        stopThreads = true;
    }

    @Override
    public void destroy() {
        buffer = null;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        event(who, ADK.cMouse, ADK.cMouseClicked, e.getModifiers(), e.getX(), e.getY(), (char) 0, 0);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        event(who, ADK.cMouse, ADK.cMousePressed, e.getModifiers(), e.getX(), e.getY(), (char) 0, 0);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        event(who, ADK.cMouse, ADK.cMouseReleased, e.getModifiers(), e.getX(), e.getY(), (char) 0, 0);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        event(who, ADK.cMouse, ADK.cMouseEntered, e.getModifiers(), e.getX(), e.getY(), (char) 0, 0);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        event(who, ADK.cMouse, ADK.cMouseExited, e.getModifiers(), e.getX(), e.getY(), (char) 0, 0);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        event(who, ADK.cMouse, ADK.cMouseDragged, e.getModifiers(), e.getX(), e.getY(), (char) 0, 0);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        event(who, ADK.cMouse, ADK.cMouseMoved, e.getModifiers(), e.getX(), e.getY(), (char) 0, 0);
    }

    @Override
    public void keyTyped(KeyEvent e) {
        event(who, ADK.cKey, ADK.cKeyTyped, e.getModifiers(), lx, ly, e.getKeyChar(), e.getKeyCode());
    }

    @Override
    public void keyPressed(KeyEvent e) {
        event(who, ADK.cKey, ADK.cKeyPressed, e.getModifiers(), lx, ly, e.getKeyChar(), e.getKeyCode());
    }

    @Override
    public void keyReleased(KeyEvent e) {
        event(who, ADK.cKey, ADK.cKeyReleased, e.getModifiers(), lx, ly, e.getKeyChar(), e.getKeyCode());
    }

    int lx = 0;
    int ly = 0;
    boolean sendingEvent = false;
    final LinkedList accumulatedEvents = new LinkedList();
    private void event(long l, int family, int id, int modifiers, int x, int y, char keychar, int keycode) {
        lx = x;
        ly = y;
        try {

            Jo e = new Jo();
            UJson.add(e, "who", l);
            UJson.add(e, "family", family);
            UJson.add(e, "id", id);
            UJson.add(e, "modifiers", modifiers);
            UJson.add(e, "x", x);
            UJson.add(e, "y", y);
            UJson.add(e, "keychar", (int) keychar);
            UJson.add(e, "keycode", (int) keycode);

            synchronized (accumulatedEvents) {
                if (sendingEvent) {
                    accumulatedEvents.add(e);
                    return;
                }
                accumulatedEvents.add(e);
            }
            pendingEvents.addWork(new IRefresh() {

                @Override
                public void refresh(IOut _) {
                    sendAccumulated();
                }
            });

        } catch (Exception _x) {
            _x.printStackTrace();
        }
    }

    private void sendAccumulated() {
        try {
            Object[] es = null;
            synchronized (accumulatedEvents) {
                es = accumulatedEvents.toArray();
                accumulatedEvents.clear();
                if (es.length > 0) {
                    sendingEvent = true;
                } else {
                    return;
                }
            }


            Jo r = new Jo();
            UJson.add(r, "secretkey", "Unknown");
            UJson.add(r, "username", "User");
            UJson.add(r, "password", "Password");
            UJson.add(r, "type", "event");
            UJson.add(r, "who", who);

            UJson.add(r, "events", es.length);
            for (int i = 0; i < es.length; i++) {
                UJson.add(r, "event" + i, (Jo) es[i]);
            }
            message = "Sending...";
            eventsService.request(_, r, new IAsyncResponse<Jo>() {

                @Override
                public void response(IOut _, final Jo response) {
                    synchronized (accumulatedEvents) {
                        sendingEvent = false;
                    }
                    message = "Idle";
                    sendAccumulated();
                }

                @Override
                public void error(IOut _, Throwable _t) {
                    synchronized (accumulatedEvents) {
                        sendingEvent = false;
                    }
                    _t.printStackTrace();
                }
            });
        } catch (Exception _x) {
            synchronized (accumulatedEvents) {
                sendingEvent = false;
            }
            _x.printStackTrace();
        }
    }

    private void painter(long l) {
        try {
            Jo r = new Jo();
            UJson.add(r, "secretkey", "Unknown");
            UJson.add(r, "username", "User");
            UJson.add(r, "password", "Password");
            UJson.add(r, "type", "painter");
            UJson.add(r, "who", l);

            paintService.request(_, r, new IAsyncResponse<Jo>() {
                @Override
                public void response(IOut _, final Jo response) {
                    pendingPaints.addWork(new IRefresh() {
                        @Override
                        public void refresh(IOut _) {
                            message = "Painting...";
                            paint(_, response);
                            message = "Idle";
                        }
                    });
                }

                @Override
                public void error(IOut _, Throwable _t) {
                    _t.printStackTrace();
                }
            });
        } catch (Exception _x) {
            _x.printStackTrace();
        }
    }

    private void paint(IOut _, Jo _paint) {
        if (UJson.has(_paint, "error")) {
            try {
                message = _paint + " :(";
                _paint.toString();
            } catch (Exception ex) {
                message = ":(";
            }
            return;
        }
        try {
            _paint = UJson.getObject(_paint, "return");

            int w = UJson.getInt(_paint, "w");
            int h = UJson.getInt(_paint, "h");
            int count = UJson.getInt(_paint, "updates");
            if (count == 0) {
                return;
            }
            if (buffer == null || buffer.getWidth(null) != w || buffer.getHeight(null) != h) {
                buffer = createImage(w, h);//new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }
            if (buffer == null) {
                return;
            }
            for (int i = 0; i < count; i++) {
                updates.insertLast(new Update(
                        buffer,
                        UJson.getString(_paint, i + ".update.rpp"),
                        UJson.getInt(_paint, i + ".update.x"),
                        UJson.getInt(_paint, i + ".update.y"),
                        UJson.getInt(_paint, i + ".update.w"),
                        UJson.getInt(_paint, i + ".update.h")));

            }
            if (getWidth() != w || getHeight() != h) {
                changeSize(w, h);
                doLayout();
            }
            repaint();
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    class Update {

        Image image;
        FilerCanvas c;
        int ux;
        int uy;
        int uw;
        int uh;

        Update(Image _image, String _base64, int _ux, int _uy, int _uw, int _uh) {
            image = _image;
            ux = _ux;
            uy = _uy;
            uw = _uw;
            uh = _uh;
            byte[] data = UBase64.decode(_base64);
            c = new FilerCanvas(who, new RPPFiler(data), new ICallback() {

                @Override
                public Object callback(Object _value) {
                    return _value;
                }
            });
        }

        public void renderToBuffer() {
            try {
                Graphics g = image.getGraphics();
                c.renderTo(new GlueAWTGraphicsToCanvas(who, g));
                g.dispose();
            } catch (Exception x) {
                x.printStackTrace();
            }
        }

        public void rendertTo(Graphics _g) {
            _g.drawImage(image, ux, uy, ux + uw, uy + uh, ux, uy, ux + uw, uy + uh, null);
        }
    }
}

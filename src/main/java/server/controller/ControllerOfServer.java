package server.controller;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;
import server.model.Model;
import server.model.User;
import server.view.ServerView;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
import java.util.*;


/**
 * Created by Фокстрот on 15.02.2016.
 */
public class ControllerOfServer extends Observable {
    private final int                       PORT=1025;
    private ServerSocket                    socket;
    private List<ClientsThread>             activeUsers;
    private Model                           model;
    private static final Logger             logger = Logger.getLogger(ControllerServer.class);
    private ServerView                      serverGUI;
    private volatile boolean                finish = false;
  //  private List<java.util.Observer> observers = new ArrayList<>();

    public List<String> getUserListString(){
        ArrayList<String> userList = new ArrayList<>();
        for(int i=0;i<activeUsers.size();i++){
            userList.add(activeUsers.get(i).getUser().getLogin());
        }
        return userList;

    }

    public void addActiveUser(ClientsThread activeUser){
        activeUsers.add(activeUser);
        this.setChanged();
        notifyObservers();

    }

    public void removeActiveUser(ClientsThread activeUser){
        activeUsers.remove(activeUser);
        this.setChanged();
         notifyObservers();
    }

    public boolean isFinish() {
        return finish;
    }

    /**
     * Default constructor of servers controller.
     */
    public ControllerOfServer()throws  IOException,org.xml.sax.SAXException{
        run();
    }
    /**
     * GUI constructor of servers controller.
     * @param serverGUI GUI of servers controller.
     */
    public ControllerOfServer(ServerView serverGUI){
        this.serverGUI = serverGUI;
    }

    /**
     * Registration the user on the server.
     * @param client thread of client.
     * @param login is String login of user.
     * @param password is String password of user.
     * @throws javax.xml.transform.TransformerException if method send() has mistake.
     */
    public synchronized void registration (ClientsThread client, String login, String password)throws javax.xml.transform.TransformerException{

        User createUser = new User();
        createUser.setLogin(login);
        createUser.setPassword(password);
        createUser.setBan(false);
        createUser.setIsAdmin(false);
        if(model.addUser(createUser)) {
            client.getXmlUser().setIdUser(createUser.getId());
            client.setUser(createUser);
             addActiveUser(client);
            client.setAuthentication(true);

            client.getXmlUser().setList(getUserListString());
            client.getXmlUser().setMessage(Preference.ActiveUsers.name());
            client.sendMessage(Preference.Registration.name());

            this.displayInfoLog("New user: " + client.getUser().getLogin() + "  is welcome.");
            logger.debug("Create new user " + client.getUser().getLogin());
        }
        else{
            throw new IllegalArgumentException("This user has already been created.");

        }
    }

    /**
     * Authenticates the user on the server.
     * @param client thread of client.
     * @throws javax.xml.transform.TransformerException if method send() has mistake.
     */
    public synchronized void authorization(ClientsThread client)throws javax.xml.transform.TransformerException{
        boolean online=false;
        String preference = client.getXmlUser().getPreference();
        List<String> data = client.getXmlUser().getList();
        int idUser = model.authorizationUser(data.get(0), data.get(1));
        /// регистрация
        if (preference.compareToIgnoreCase(Preference.Registration.name()) == 0){
            try {
                if (idUser == -1) {
                    registration(client, data.get(0), data.get(1));
                }
                else {
                    throw new IllegalArgumentException("This user has already been created.");
                }
            }
            catch (IllegalArgumentException e){
                client.getXmlUser().setMessage(Preference.IncorrectValue.name() + " name of user. This user has already been created.");
                client.sendMessage(Preference.Registration.name());
            }
        }
        ///авторизация
        else {
            if (preference.compareToIgnoreCase(Preference.Authentication.name()) == 0) {
                if (idUser != -1) {
                    client.getXmlUser().setIdUser(idUser);
                    client.setUser(model.getUser(idUser));

                    //проверка пользователя
                    for (int i = 0; i < activeUsers.size(); i++) {
                        if (activeUsers.get(i).getUser().getLogin().compareToIgnoreCase(client.getUser().getLogin()) == 0) {
                            online = true;
                            client.getXmlUser().setMessage("The user is online.");
                            client.sendMessage(Preference.Authentication.name());
                            break;
                        }
                    }
                    if (!online) {
                        client.setAuthentication(true);
                        logger.debug("Authentications user is " + client.getUser().getLogin());
                        if (client.getUser().isAdmin()) {
                            this.displayInfoLog("Admin: " + client.getUser().getLogin() + " is welcome.");
                        } else {
                            this.displayInfoLog(client.getUser().getLogin() + " is welcome.");
                        }
                        //send message to client of active user list and data of client

                        //client.getXmlUser().setList(getUserListString());
                        if (client.getUser().isBan()) {
                            client.getXmlUser().setMessage(Preference.Ban.name());
                        }
                      else {
                            client.getXmlUser().setMessage(Preference.ActiveUsers.name());
                        }

                        if (client.getUser().isAdmin()) {
                            client.sendMessage(Preference.Admin.name());

                        }
                        else {
                            client.sendMessage(Preference.Authentication.name());
                        }
                      //if(!client.getUser().isAdmin()) {
                            addActiveUser(client);
                    //  }
                    }
                }
                else{
                    client.getXmlUser().setMessage("User does not exist!");
                    client.sendMessage(Preference.Authentication.name());
                }

            }

            else{
                client.getXmlUser().setMessage("The client is not authenticated. No token \"authentication\"  word. Please try to connect again.");
                client.sendMessage(Preference.Authentication.name());
            }
        }

    }

    /**
     * Method for display information on servers GUI or info message of lOG.
     * @param message is String information of server.
     */
    public void displayInfoLog(String message){
        if(serverGUI!=null){
            serverGUI.display(message);
        }
        logger.info(message);

    }

    /**
     * Method for write exception on servers GUI to LOG error message.
     * @param message is exception of server.
     */
    public void catchGuiException(Exception message) {
        logger.error("Server GUI: ", message);

    }
    /**
     * Method for start work of server.
     * @throws IOException if port don't build; wrong of client's socket.
     * @throws org.xml.sax.SAXException if ServerThread has mistake of xml
     */
    public void run()throws IOException, org.xml.sax.SAXException{
        model = new Model();
        activeUsers = new ArrayList<>();
        this.displayInfoLog("Building to port " + this.PORT + ", please wait  ...");
        socket = new ServerSocket(PORT);
        this.displayInfoLog("Server started.");
        this.displayInfoLog("Waiting a client... ");
        while (true) {
            Socket client = socket.accept();
            logger.debug("Connection from " + client.getInetAddress().getHostName());
           // ClientsThread newClient= ;
            this.addObserver(new ClientsThread(client, this));

        }

    }
    public void stop() throws IOException{
        this.finish=true;
        if (socket != null) {
            socket.close();
        }
        this.displayInfoLog("\n" + "Server is stopped." + "\n");
        model.stop();

    }


    /**
     * Method, that reads client's preference, handles it and sends answer to client.
     * @param client thread of client.
     * @param preference preference of client's message.
     * @throws javax.xml.transform.TransformerException if transformation xml to OutputStream has mistake.
     */
    public synchronized void readCommand(ClientsThread client,Preference preference) throws javax.xml.transform.TransformerException{
        switch (preference){

            case MessageForAll:
                String messageToChat = client.getXmlUser().getMessage();
                for(int i=0;i<activeUsers.size();i++){
                    activeUsers.get(i).getXmlUser().setMessage(messageToChat);
                    activeUsers.get(i).getXmlUser().setList(getUserListString());
                    activeUsers.get(i).sendMessage(Preference.MessageForAll.name());
                }
                // this.displayInfoLog("User: "+client.getUser().getLogin()+" send message to all. ");
                logger.debug("Send message to all: " + messageToChat);
                break;

            case PrivateMessage:
                String messageToPrivateChat = client.getXmlUser().getMessage();
                List<String> userList = client.getXmlUser().getList();
                if(userList!=null) {
                    int keyDialog=client.getXmlUser().getKeyDialog();
                    List<String> privateList = new ArrayList<>();
                    privateList.add(client.getUser().getLogin());
                    privateList.addAll(userList);
                    client.sendMessage(Preference.PrivateMessage.name());
                    for(int j=0;j<activeUsers.size();j++){
                        for (int i = 0; i < userList.size(); i++) {
                            if (activeUsers.get(j).getUser().getLogin().compareToIgnoreCase(userList.get(i)) == 0) {
                                activeUsers.get(j).getXmlUser().setMessage(messageToPrivateChat);
                                activeUsers.get(j).getXmlUser().setList(privateList);
                                activeUsers.get(j).getXmlUser().setKeyDialog(keyDialog);
                                activeUsers.get(j).sendMessage(Preference.PrivateMessage.name());
                            }
                        }
                    }
                    logger.debug("Send private message: " +messageToPrivateChat+" to users: "+userList.toString());
                }
                else{
                    client.getXmlUser().setMessage(Preference.IncorrectValue.name());
                    client.sendMessage(Preference.PrivateMessage.name());
                }
                // this.displayInfoLog("User: "+client.getUser().getLogin()+" send private message to users: "+userList.toString());

                break;

          /*  case ActiveUsers:
                client.getXmlUser().setList(getUserListString());
                client.getXmlUser().setMessage(Preference.ActiveUsers.name());
                client.sendMessage(Preference.ActiveUsers.name());
                // this.displayInfoLog("Send list of active user to user: " + client.getUser().getLogin());
                // this.displayInfoLog(getUserListString().toString());
                break;

            */
            case Ban:
                if(client.getUser().isAdmin()) {
                    String infoFoBan = client.getXmlUser().getList().get(0);
                    if (model.setBan(infoFoBan, true)) {
                        client.getXmlUser().setMessage(Preference.Successfully.name());
                        client.sendMessage(Preference.Ban.name());
                    }
                    for(int i=0;i<activeUsers.size();i++){
                        if (activeUsers.get(i).getUser().getLogin().compareToIgnoreCase(infoFoBan) == 0) {
                            //if(activeUsers.get(i).getUser().isBan()){
                            activeUsers.get(i).getXmlUser().setMessage(Preference.Ban.name());
                            activeUsers.get(i).sendMessage(Preference.Ban.name());
                            break;

                            // }
                        }
                    }
                }
                break;

            case UnBan:
                if(client.getUser().isAdmin()) {
                    String infoFoBan2 = client.getXmlUser().getList().get(0);
                    List<String> banUsers = model.getBanList();
                    for(int i=0;i<banUsers.size();i++){
                        if (banUsers.get(i).compareToIgnoreCase(infoFoBan2) == 0) {
                            if(model.setBan(infoFoBan2,false)) {
                                client.getXmlUser().setMessage(Preference.Successfully.name());
                                break;
                            }
                        }
                    }
                    for(int i=0;i<activeUsers.size();i++){
                        if (activeUsers.get(i).getUser().getLogin().compareToIgnoreCase(infoFoBan2) == 0) {
                            if(!activeUsers.get(i).getUser().isBan()){
                                activeUsers.get(i).getXmlUser().setMessage("You was unban");
                                activeUsers.get(i).sendMessage(Preference.UnBan.name());
                                break;

                            }
                        }
                    }
                    this.displayInfoLog("Admin "+ Preference.UnBan.name()+" user:  " + infoFoBan2);
                }

                client.sendMessage(Preference.UnBan.name());
                break;

            case BanUsers:
                List<String> banUsers = model.getBanList();
                client.getXmlUser().setList(banUsers);
                client.getXmlUser().setMessage(Preference.BanUsers.name());
                client.sendMessage(Preference.BanUsers.name());
              /*  if(client.getUser().isAdmin()) {
                   addActiveUser(client);
               }
*/
                break;

            case Edit:
                List<String> newUser = client.getXmlUser().getList();
                client.getUser().setLogin(newUser.get(0));
                client.getUser().setPassword(newUser.get(1));
                model.editUser(client.getUser());
                client.getXmlUser().setMessage(Preference.Successfully.name());
                client.sendMessage(Preference.Edit.name());
                this.displayInfoLog("Edit of user: " + client.getUser().getLogin() + " is successful. ");
                logger.debug(Preference.Edit.name()+" user: "+client.getUser().getLogin());
                break;

            case Remove:
                //проверка на админа
                if(client.getUser().isAdmin()){
                    String removeUser = client.getXmlUser().getList().get(0);
                    for(int i=0;i<activeUsers.size();i++){
                        if(activeUsers.get(i).getUser().getLogin().compareToIgnoreCase(removeUser)==0){
                            model.removeUser(activeUsers.get(i).getUser());

                            activeUsers.get(i).getXmlUser().setMessage("Admin deleted you.");
                            activeUsers.get(i).sendMessage(Preference.Remove.name());

                            activeUsers.get(i).close();
                            removeActiveUser(activeUsers.get(i));


                            client.getXmlUser().setMessage(Preference.Successfully.name());
                            client.sendMessage(Preference.Remove.name());
                            break;

                        }
                    }

                    this.displayInfoLog("Admin remove user: " + removeUser);
                    logger.debug(Preference.Remove.name()+ " removeUser");
                }
                else{
                    //удаление самого пользователя
                    model.removeUser(client.getUser());
                    removeActiveUser(client);
                    client.getXmlUser().setMessage(Preference.Successfully.name());
                    client.sendMessage(Preference.Remove.name());
                    client.close();
                    this.displayInfoLog("Server remove user:  " + client.getUser().getLogin());
                    logger.debug("Remove " + client.getUser().getLogin());
                }
                //client.getXmlUser().setMessage(Preference.Successfully.name());
                // client.sendMessage(Preference.Remove.name());
                break;

            case Close:

                client.close();
                removeActiveUser(client);
                this.displayInfoLog("User: " + client.getUser().getLogin() + " close.");
                break;

            default:
                client.sendMessage(Preference.IncorrectValue.name());
                break;
        }





    }

    public static void main(String[] args)throws IOException, ParseException,SAXException {
        new ControllerOfServer();
    }


}
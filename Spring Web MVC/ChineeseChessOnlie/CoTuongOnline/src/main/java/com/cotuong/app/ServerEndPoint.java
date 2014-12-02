package com.cotuong.app;

import com.cotuong.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;


@javax.websocket.server.ServerEndpoint(value="/game")
public class ServerEndPoint {
	
	private static Set<Session> list = Collections.synchronizedSet(new HashSet<Session>());
	private static HashMap<String,String> list_user = new HashMap<String,String>();
	/**
	 * 1 entry list_match (ID_A,ID_B)
	 */
	private static HashMap<String, String> list_match = new HashMap<String,String>();

	@Autowired
	AccountService accountService;

	@OnOpen
	public void onOpen(Session session) throws IOException{
		System.out.println(session.getId() + " : connected ... ");
		session.getBasicRemote().sendText("Session id "+session.getId());
		list.add(session);
	}
	   
	@OnClose
	public void onClose(Session session, CloseReason closeReason) throws IOException{
		System.out.printf("Session %s closed because of %s", session.getId(), closeReason.getReasonPhrase());
		list.remove(session);
		String clientId=list_user.get(session.getId());
		removeCurrentUserInList(session,clientId,list_user);
		accountService.setStatusOffline(clientId);
		if(list_match.containsKey(clientId)){

		}else if(list_match.containsValue(clientId)){

		}
	}
	   
	@OnMessage
	public void onMessage(Session session,String data_receive) throws IOException,SQLException{
		String[] data=data_receive.split("-");
		switch (data[0]) {
			case "REG":
				/**
				 *  Every client whom connect to server have to post their email to the socket server
				 *  Then save user's email into list_user
				 *  List<SessionId,EmailPlayerConnect> list_user (String,String)
				 *  Client send : REG-ID_Client
				 *  Server check user in list_user
				 *  Server save (client_session_id,client_id) to list_user
				 *  Then server send "REG-|-OK" to client
				 *  data[1]=ID_Client
				 */
				removeCurrentUserInList(session,data[1],list_user);
				list_user.put(session.getId(),data[1]);
				session.getBasicRemote().sendText("REG-|-OK");
				break;
			case "REQHANDSHAKE":
				/**
				 * User A wanna play with B
				 * User A : Send "REQHANDSHAKE-ID_B" to server
				 * Server : Find ID_B in list_user -> Got session of user's B -> send req to B
				 * User B : Receive "REQHANDSHAKE-|-ID_A"
				 */
				checkAndSendMsgToUser(data, session, "REQHANDSHAKE-|-");
				break;
			case "REPHANDSHAKE":
				/**
				 * Reply the handshake to player who send the request.
				 * User B : Send "REPHANDSHAKE-ID_A-BOOL"
				 *  - BOOL
				 * 		+ Accept  : 0
				 * 		+ Decline : 1
				 * Server : Find ID_A in list_user -> session of user's A -> send to A
				 * User A : Receive "REPHANDSHAKE-BOOL-ID_B"
				 * Save id 2 player to list_match
				 * data[1]=ID_A
				 * data[2]=BOOL
				 */
				checkAndSendMsgToUser(data,session,"REPHANDSHAKE-|-"+data[2]+"-|-");
				list_match.put(data[1],list_user.get(session.getId()));
				break;
			case "END":
				/**
				 * A send the end signal to player B
				 * Send : "END-ID_B-ID_A" to server
				 * B receive : END-ID_A
				 */
				removeCurrentUserInList(session,data[2],list_match);
				checkAndSendMsgToUser(data,session,"END-|-");
				break;
			case "REQPAUSE":
				/**
				 * User A request pause game to user B
				 * User A : Send "REQPAUSE-ID_B"
				 * Server : Find ID_B in list_user -> session of user's B -> send to B
				 * User B : Receive "REQPAUSE-ID_A"
				 */
				checkAndSendMsgToUser(data,session,"REQPAUSE-|-");
				break;
			case "REPPAUSE":
				/**
				 * User B reply pause or not
				 * User B : Send "REPPAUSE-ID_A-BOOL"
				 *  - BOOL
				 * 		+ Accept  : 0
				 * 		+ Decline : 1
				 *  - ID_A 	: Session id of player to response the request handshake
				 * Server : Find ID_A in list_user -> session of user's A -> send to A
				 * User A : Receive "REPPAUSE-BOOL-ID_B"
				 */
				checkAndSendMsgToUser(data,session,"REPAUSE-|-"+data[1]+"-|-");
				break;
			case "REQNEWGAME":
				/**
				 * User A request pause game to user B
				 * User A : Send "REQNEWGAME-ID_B"
				 * Server : Find ID_B in list_user -> session of user's B -> send to B
				 * User B : Receive "REQNEWGAME-ID_A"
				 */
				checkAndSendMsgToUser(data,session,"REQNEWGAME-|-");
				break;
			case "REPNEWGAME":
				/**
				 * User B reply to user A : play new game or not
					* User B : Send "REPHANDSHAKE-ID_A-BOOL"
					*  - BOOL
					* 		+ Accept  : 0
					* 		+ Decline : 1
					*  - ID 	: Session id of player to response the request handshake
					* Server : Find ID_A in list_user -> session of user's A -> send to A
					* User A : Receive "REPHANDSHAKE-BOOL-ID_B"
					*/
				checkAndSendMsgToUser(data,session,"REPNEWGAME-|-"+data[1]+"-|-");
				break;
			case "LOSE":
					/**
					 * User A accept lose this current game
					 * User A : Send "LOSE-ID_B"
					 * Server : Find ID_B in list_user -> session of user's B -> send to B
					 * User B : Receive "LOSE-ID_A" . (B no need to return any message)
					 */
				checkAndSendMsgToUser(data,session,"LOSE-|-");
				break;
			case "CHAT":
					/**
					 * Handle message between 2 player in current match
					 * User A : Send "CHAT-ID_B-MESSAGE"
					 * 	- ID 		: Session id player receive the message
					 * 	- MESSAGE   : Message data
					 */
						String send_to=data[1];
						try {
							System.out.println("Client nhận message:"+send_to);
							System.out.println("Nội dung message :"+data[2]);
//							session_to.getBasicRemote().sendText("CHAT-|-"+data[2]);
						} catch (Exception ex) {
							System.out.println("Client không online");
						}
				break;
			case "MOVE":
				break;
//			   case ""
			default:
				break;
		   }
	   }

	/**
	 *
	 * @param data
	 * @param session
	 * @param msg
	 * @throws IOException
	 */
	public void checkAndSendMsgToUser(String[] data,Session session,String msg) throws IOException{
		if(list_user.containsValue(data[1])){
			String session_id="";
			for (String key : list_user.keySet()){
				if(list_user.get(key)==data[1]){
					session_id=key;
				}
			}
			for(Session sess : list){
				if(sess.getId().equalsIgnoreCase(session_id)){
					String id_a=list_user.get(session.getId());
					sess.getBasicRemote().sendText(msg+id_a);
					return;
				}
			}
			return ;
		}
			session.getBasicRemote().sendText("RESULT-|-User is offline");
	}

	/**
	 *
	 * @param session
	 * @param userID
	 * @param list
	 */
	public void removeCurrentUserInList(Session session,String userID,HashMap<String,String> list){
		if(list.get(session.getId())==userID){
			list.remove(session.getId()); // if found then delete
		}
	}

}

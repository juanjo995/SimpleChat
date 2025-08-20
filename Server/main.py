from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from typing import List
import time
import asyncio
import os
import random
import string
from datetime import datetime
import requests
from google.oauth2 import service_account
from google.auth.transport.requests import Request

# Path to your Firebase service account key
SERVICE_ACCOUNT_FILE = "your_json_file_here"

# Your Firebase project ID
PROJECT_ID = "your_project_id_here"

# Load credentials
credentials = service_account.Credentials.from_service_account_file(
    SERVICE_ACCOUNT_FILE,
    scopes=["https://www.googleapis.com/auth/firebase.messaging"]
)

# Refresh token (get access token)
credentials.refresh(Request())
access_token = credentials.token

# FCM endpoint
url = f"https://fcm.googleapis.com/v1/projects/{PROJECT_ID}/messages:send"

rootPath = "./"
#rootPath = "/"

pongIDs: list[str] = []

myLock = asyncio.Lock()

app = FastAPI()

def log(logMessage: str):

    now = datetime.now()
    hour = now.hour
    minute = now.minute
    hourStr = ""
    minuteStr = ""
    if hour < 10:
        hourStr = "0" + str(hour)
    else:
        hourStr = str(hour)
    if minute < 10:
        minuteStr = "0" + str(minute)
    else:
        minuteStr = str(minute)
    timestamp = hourStr + ":" + minuteStr + " "
    print(timestamp + logMessage)
    with open(rootPath + "app/log.txt", "a") as file:
        file.write(timestamp + logMessage + "\n")

def getRandomString(length: int = 10) -> str:
    characters = string.ascii_letters + string.digits
    return ''.join(random.choices(characters, k=length))

def userExists(userName: str) -> bool:
    Users = []
    with open(rootPath + "app/users.txt", "r") as file:
        for line in file:
            Users.append(line.strip())
    for user in Users:
        if user == userName:
            return True
    return False

def get_connection_by_username(self, username: str) -> WebSocket | None:
    for ws, user in self.active_connections.items():
        if user == username:
            return ws
    return None

async def receivePong(ID: str):
    while True:
        if ID in pongIDs:
            pongIDs.remove(ID)
            break
        await asyncio.sleep(0.1)

class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[WebSocket, str] = {}

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections[websocket] = ""
        log("New connection: " + self.active_connections[websocket])
        log("Active connections: " + str(len(manager.active_connections)))

    def disconnect(self, websocket: WebSocket):
        username = self.active_connections.pop(websocket, None)
        log(f"Disconnected: {username}")
        log("Active connections: " + str(len(manager.active_connections)))
        
    async def receive_message(self, message: str, sender: WebSocket):
        
        if message == "ping":
            log("Received ping from " + self.active_connections[sender] + " sending pong")
            await sender.send_text("pong")
        
        if message.split(";")[0] == "pong":
            pongID = message.split(";")[1]
            pongIDs.append(pongID)
            return
        
        if message.split(";")[0] == "newFCMtoken":
            with open(rootPath + "app/users/" + self.active_connections[sender] + "/FCMtoken.txt", "w") as file:
                file.write(message.split(";")[1])
        
        if message.split(";")[0] == "userExists":
            if userExists(message.split(";")[1]):
                await sender.send_text("Server: OK, the user exists")
            else:
                await sender.send_text("Server: Error, the user doesn't exists")
            return
        
        if message.split(";")[0] == "registerNewUser":
            userName = message.split(";")[1]
            publicKey = message.split(";")[2]
            FCMtoken = message.split(";")[3]
            with open(rootPath + "app/users.txt", "a") as file:
                file.write(userName + "\n")
            os.makedirs(rootPath + "app/users/" + userName, exist_ok=True)
            with open(rootPath + "app/users/" + userName + "/publicKey.txt", "w") as file:
                file.write(publicKey)
            open(rootPath + "app/users/" + userName + "/queue.txt", "w").close()
            self.active_connections[sender] = userName
            
            with open(rootPath + "app/users/" + userName + "/FCMtoken.txt", "w") as file:
                file.write(FCMtoken)
            
            log("User " + userName + " added")
            return
        
        if message.split(";")[0] == "getPublicKey":
            publicKey = ""
            with open(rootPath + "app/users/" + message.split(";")[1] + "/publicKey.txt", "r") as file:
                publicKey = file.read()
            await sender.send_text(publicKey)
            return
            
        if message.split(";")[0] == "asociateUserToConexion":
            userName = message.split(";")[1]
            indexToDelete = -1
            for index, (websocket, username) in enumerate(self.active_connections.items()):
                if username == userName:
                    indexToDelete = index
            if indexToDelete != -1:
                items = list(self.active_connections.items())
                key_to_delete, _ = items[indexToDelete]
                del self.active_connections[key_to_delete]
                log("Revomed conecction with the same username: " + userName)
            
            self.active_connections[sender] = userName
            log("Asociated user " + userName + " to webSocket connection")
            log("Active connections: " + str(len(manager.active_connections)))
            return
        
        if message.split(";")[0] == "sendMessage":
            async with myLock:
                receiver = message.split(";")[1]
                senderName = self.active_connections[sender]
                messageID = message.split(";")[2]
                messageText = message.split(";")[3]
                messageText = messageText.replace("\n", "")
                messageTime = message.split(";")[4]
                
                connection = get_connection_by_username(self, receiver)
                isConnectionAlive = False
                pingID = getRandomString()
                
                if connection != None:
                    try:
                        await connection.send_text("ping;" + pingID)
                        await asyncio.wait_for(receivePong(pingID), timeout = 5.0)
                        isConnectionAlive = True
                    except asyncio.TimeoutError:
                        self.disconnect(connection)
                        isConnectionAlive = False
                
                if isConnectionAlive == False:
                    with open(rootPath + "app/users/" + receiver + "/queue.txt", "a") as file:
                        log("Enqueueing message from " + senderName + " -> " + receiver)
                        file.write(senderName + ";" + messageID + ";" + messageText + ";" + messageTime + "\n")
                    
                    DEVICE_TOKEN = ""
                    with open(rootPath + "app/users/" + receiver + "/FCMtoken.txt", "r") as f:
                        DEVICE_TOKEN = f.readline()
                    
                    credentials.refresh(Request())
                    access_token = credentials.token

                    payload = {
                              "message": {
                                "token": DEVICE_TOKEN,
                                "android": {
                                  "priority": "high",
                                  "data": {
                                    "type": "new_message"
                                  }
                                }
                              }
                            }

                    headers = {
                        "Authorization": f"Bearer {access_token}",
                        "Content-Type": "application/json; UTF-8",
                    }
                    response = requests.post(url, headers=headers, json=payload)
                    
                else:
                    await connection.send_text("newMessage;" + senderName + ";" + messageID + ";" + messageText + ";" + messageTime)
                    log("Sending message from " + senderName + " -> " + receiver)
        
        if message.split(";")[0] == "deliveredMessage":
            connection = get_connection_by_username(self, message.split(";")[1])
            
            isConnectionAlive = False
            pingID = getRandomString()
            
            if connection != None:
                try:
                    await connection.send_text("ping;" + pingID)
                    await asyncio.wait_for(receivePong(pingID), timeout = 5.0)
                    isConnectionAlive = True
                except asyncio.TimeoutError:
                    self.disconnect(connection)
                    isConnectionAlive = False
            
            if isConnectionAlive == False:
                with open(rootPath + "app/users/" + message.split(";")[1] + "/queue.txt", "a") as file:
                    file.write("deliveredMessage;" + self.active_connections[sender] + ";" + message.split(";")[2] + "\n")
            else:
                await connection.send_text("deliveredMessage;" + self.active_connections[sender] + ";" + message.split(";")[2])
            
        if message.split(";")[0] == "messagesForMe?":
            senderName = self.active_connections[sender]
            if userExists(senderName):
                with open(rootPath + "app/users/" + senderName + "/queue.txt", "r") as file:
                    lines = file.readlines()
                    for line in lines:
                        line = line.rstrip('\n')
                        parts = line.split(";")
                        if parts[0] == "deliveredMessage":
                            await sender.send_text(parts[0] + ";" + parts[1] + ";" + parts[2])
                            await asyncio.sleep(0.1)
                        else:
                            await sender.send_text("newMessage;" + parts[0] + ";" + parts[1] + ";" + parts[2] + ";" + parts[3])
                            await asyncio.sleep(0.1)
                with open(rootPath + "app/users/" + senderName + "/queue.txt", "w") as file:
                    pass
            
manager = ConnectionManager()

@app.websocket("/app")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            await manager.receive_message(data, sender=websocket)
    except WebSocketDisconnect:
        manager.disconnect(websocket)

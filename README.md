# SimpleChat
A simple chat application to send messages

![demo](https://github.com/user-attachments/assets/700fb0e1-af43-4543-b357-fe6a1b7388e1)

This repository contains an Android chat application and the server that makes it work.

This project uses **WebSockets** to establish comunication and **Firebase Cloud Massaging** to push notifications when messages are received.

## Configure this project

In order to make the notifications work we need to configure the **Firebase Cloud Massaging**.

First we need to create a project for our app in the Firebase console.

https://console.firebase.google.com

We click on **Create a Firebase project**.

<img width="302" height="218" alt="Create project" src="https://github.com/user-attachments/assets/5d70f90d-fc45-408f-8339-969eea3091f2" />

We enter a name for our project and click on **Continue**.

When our project is created we add an Android app.

<img width="376" height="230" alt="Add Android app" src="https://github.com/user-attachments/assets/63ee5419-66d7-429a-875d-e4a9643eb213" />

We put the **Android package name**

<img width="518" height="468" alt="Package name" src="https://github.com/user-attachments/assets/d5d80da4-0e99-4dc8-a60a-c204e8a0bc1d" />

Next, we download the `google-services.json`.

<img width="718" height="463" alt="google-services.json" src="https://github.com/user-attachments/assets/76996f6f-1d0d-4121-acba-68f882357ee9" />

Now we copy this file into `Application/SimpleChat/app/` directory.

Now we go to our project settings and find the **Service accounts** tab.

<img width="314" height="126" alt="Project settings" src="https://github.com/user-attachments/assets/7df7e625-a68d-4d9a-b56a-feccb23ac4ed" />

And click on **Generate new private key**.

<img width="210" height="54" alt="Generate new private key" src="https://github.com/user-attachments/assets/0934c3c7-d20e-417c-8fbf-8062a045b6a5" />

This will download a **.json** file, we have to place it on the server side, on `Server/`

And next, we need to modify the `main.py` file.

On `SERVICE_ACCOUNT_FILE` we put the name of the file we just downloaded.

And on `PROJECT_ID` we put our project ID (We can see it under our project settings on the **General** tab).

<img width="340" height="109" alt="account_file_project_id" src="https://github.com/user-attachments/assets/b2d2fe6f-908c-48de-9564-d4ee2cf558ae" />

## Start the server

To run our server locally we are going to create a Python virtual environment.

On the same folder where our `main.py` file is located we open a terminal.

`python3 -m venv venv`

`source venv/bin/activate`

`pip install -r requirements.txt`

And run the server.

`uvicorn main:app --host 0.0.0.0 --port 8000 --reload`

## Run the application

Now just open your Android Studio and open the application project.

Open the `GlobalVars.kt` file and modify the url var with your local IP address (You can see what your local IP address is with `hostname -I`).

<img width="369" height="27" alt="url" src="https://github.com/user-attachments/assets/cd145178-bf35-475d-a7f9-6905eaa37270" />

Now find the `network_security_config.xml` file under `res/xml/`

And modify this line with your local IP address.

<img width="505" height="123" alt="domain" src="https://github.com/user-attachments/assets/bd7f003c-ece5-45b2-bbfc-da47c6631da7" />

Now you can run the application on the Android Studio emulator or on your own device and start chatting.

If you are deploying this app on a real dedicated server don't forget to use the **wss** protocol.

In the `GlobalVars.kt` file modify the url var line with something like this:

`var url: String = "wss://your_server.com"`

# Ria Jairam's ePortfolio


## Informal code review:
This code review contains my three artifacts shared in this portfolio. I have chosen to work on one product - the Weight Tracker app I coded in CS-360, mobile architecture and development. I go over the original project plan, discussing the planned functionality, then the existing code and the improvements. My plan was to convert the login and database to firebase (Google Cloud) and add a search and sort function.

[![Code Review video](https://img.youtube.com/vi/pE60ViIqgTE/maxresdefault.jpg)](https://youtu.be/pE60ViIqgTE)

## Weight Tracker App

This app was created as the project in CS-360 Mobile Architecture and Programming. The idea behind the weight tracker app is to allow the user to enter a goal weight, and then track weights daily, and alert the user if they have met their goal weight. There was also an option to send SMS messaging for alerts. For my ePortfolio and capstone project I have done three improvements in three areas - Software Design and Engineering, Data Structures and Algorithms, and Databases. Described below are the improvements.

## Artifact 1 - Software Design and Engineering
This is the first enhancement to the Weight Tracker App. It now uses Firebase for authentication. 

The original app used authentication in a local SQLite database. This was stored as password hashes and associated to usernames. This satisfied the requirement to have authentication but it had several limitations.

The first limitation is that while you could create an account and login, you were locked out of the app if you forgot your password. With Firebase, I added a "forgot password" functionality. This will allow a user to reset their password and use the industry standard practice of e-mail to do so. 

The second limitation was authentication across devices. You could store your weights on a single device, login on a single device but if you had another android device, you could not login with the same credentials on another device. Having the ability to centrally authenticate is a pre-requisite for the third enhancement, which is that the data would be accessible across devices.

The third limitation was session management. With local authentication there was only basic session management. The final app used Firebase for session management and the final app switched to unique user IDs instead of using user-provided ones. 


[Artifact 1 Narrative](https://github.com/rjairam/rjairam.github.io/blob/main/Artifacts/Artifact%201%20narrative%20%20Ria%20Jairam.pdf)

[Artifact 1 Original](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%201%20Original)

[Arctifact 1 Enhanced](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%201%20Enhanced)

## Artifact 2 - Data Structures and Algorithms
This is the second enhancement to the Weight Tracker App. In this enhancement, I have added a search and sort feature to the app. 

The search feature uses a selection sort and sorts locally for speed. 


[Artifact 2 Narrative](https://github.com/rjairam/rjairam.github.io/blob/main/Artifacts/Artifact%202%20narrative%20%20Ria%20Jairam.pdf)

[Artifact 2 Original](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%202%20Original)

[Artifact 2 Enhanced](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%202%20Enhanced)

## Artifact 3 - Databases
This is the third enhancement to the Weight Tracker App. In this enhancement, the entire database is moved to the cloud and is now stored in Firebase cloud store. Firebase cloud store acts as a document database and data can be accessed across devices.

[Artifact 3 Narrative](https://github.com/rjairam/rjairam.github.io/blob/main/Artifacts/Artifact%203%20narrative%20%20Ria%20Jairam.pdf)

[Artifact 3 Original](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%203%20Original)

[Artifact 3 Enhanced](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%203%20Enhanced)


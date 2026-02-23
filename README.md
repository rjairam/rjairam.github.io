# Ria Jairam's ePortfolio

## Introduction
I am Ria Jairam. I work currently as a Cloud Security Architect and I have been working on my Bachelors in Computer Science for a couple of years. I have been in the cybersecurity space for a while, and have worked on projects for both private organizations, government and international organizations. After I graduate I will be looking to further my skills and contribution in the field of Computer Science and Cybersecurity. I also enjoy working on cloud enabled mobile apps and seek to do work with this in the future. This portfolio has three enhancements to one project, a weight tracking app that I wrote for the Mobile Architecture and Development class (CS-360). 

## Reflection on my Education at SNHU

During my time at SNHU I learned and built upon knowledge that I had in various programming languages and technologies. I worked on projects in C++, Java and Python. I have worked on different types of databases including relational databases and document databases. I have worked on projects for graphics development, mobile development, full-stack development, and artificial intelligence (AI). I also learned about methodologies related to agile development, the software development lifecycle, and more. With the information security concentration I also learned about computer systems security, software security and computer networking.

I have also obtained several industry certifications including ISC2's CC, CISSP, CSSLP and CCSP and AWS Solutions Architect Associate.

### Some of my strengths and the things I've learned

**IT-145 - Foundation in Application Development**

In this course I did a deep dive into Java, and wrote an application to inventory animals in an animal shelter. Previously I had limited Java experience, and when I completed this course I felt much more comfortable in the language. This laid the foundation for further courses that used the Java programming language.

**DAD-220 Structured Database Environments**

In this course I built on existing knowledge I had about relational databases using MySQL. I learned quite a bit more about databases and how to query them, build them and utilize things like joins and unions. I also learned how to use the data and analyze it, and provide insights using the data. This course laid the foundation for future coursework and professional work in databases and application development 

**CS-330 Computer Graphics and Visualization**

In this course I learned about computer graphics, visualization and programmatically in C++ creating a scene from a photograph. I also learned about animation and motion graphics. The end result was a fun project of a brick breaker game and it was an interesting application of C++ code and open source graphics libraries. 

**CS-360 Mobile Architecture and Development**

In this course I learned Android development. I had written apps for iOS before, but Android was a new adventure. I created a weight tracker app which proved quite interesting to develop in Java. In this portfolio I will expand upon the app and make it a truly cloud native app that can be used for weight management. I learned about Android permissions, layouts, and using the Android Studio IDE. It opened new doors for me and gave me areas to explore for career areas I may want to go into. Since I enjoy working with IoT devices and mobile, this may be an area I would look to go into in the future.


## To the future

I plan to further my studies in computer science and cybersecurity, with the goal of becoming a recognized expert in the field. I also want to continue work in mobile development and with the proliferation of IoT devices I am looking forward to using my skills in Android and iOS development to investigate that area of security as well. 

Throughout my career I have focused on continuous learning and I will continue to do so for the foreseeable future. My studies at SNHU and beyond are a major part of this and I plan to continue to learn and share knowledge as much as I can. 

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

#### Lessons learned

When enhancing this part of the application I had to learn how to use firebase cloud. I also had to learn how to use the credentials, and secure them. Learning how Firebase cloud authentication worked has inspired me to look at using it for other mobile projects. 

#### Artifact files

[Artifact 1 Narrative](https://github.com/rjairam/rjairam.github.io/blob/main/Artifacts/Artifact%201%20narrative%20%20Ria%20Jairam.pdf)

[Artifact 1 Original](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%201%20Original)

[Arctifact 1 Enhanced](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%201%20Enhanced)

## Artifact 2 - Data Structures and Algorithms
This is the second enhancement to the Weight Tracker App. In this enhancement, I have added a search and sort feature to the app. Users can search by date or weight, and sort ascending or descending with a single tap on the date or weight fields. 

The sort feature uses a selection sort and sorts locally for speed. In this phase of the project, I did not yet move the database to firebase cloud, as that will be in Enhancement three. The data is still stored locally in SQLite. However, the search and sort feature works well. The app pre-loads the data, then displays it. There are UI elements that allow you to quickly change the sorting by weight or date. This sort feature uses a merge sort internally, which makes the sorting very fast.



#### Lessons learned

I experimented with a few sorting algorithms before I settled on a merge sort. The criteria for choosing this particular algorithm primarily centered on performance and stability.

Enhancing and modifying the artifact was not so straightforward. I had to make decisions as to which algorithm to use. I decided on merge sort because it is stable and consistent. It has an O (n log n) time complexity. It is a classic divide and conquer strategy. Getting some of the ascending and descending sort to work properly took trial and error. I had to make UI decisions for both portrait and landscape. I coded both because Google’s UI design guidelines for Android since Android 16 now require both landscape and portrait. 

#### Artifact files
[Artifact 2 Narrative](https://github.com/rjairam/rjairam.github.io/blob/main/Artifacts/Artifact%202%20narrative%20%20Ria%20Jairam.pdf)

[Artifact 2 Original](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%202%20Original)

[Artifact 2 Enhanced](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%202%20Enhanced)

## Artifact 3 - Databases
This is the third enhancement to the Weight Tracker App. In this enhancement, the entire database is moved to the cloud and is now stored in Firebase cloud store. Firebase cloud store acts as a document database and data can be accessed across devices.

This enhancement brings the app fully into the cloud. You can run the app on one device, enter and retrieve data and then go to another device, login with your firebase credentials and then access the data you created on another device.

For this enhancement, we are moving the data from a relational database (SQLite) into a document database (Firebase cloud store). This does change performance of the database queries, however since search and sort is now handled locally there is less reliance on the document DB to do these and thus performance will not be affected. 

#### Lessons Learned

The major takeaway from this is how to get Firebase cloud store working. The other takeaway is how to use data that was previously in a relational database and into a document database. I had to also do data migration from the local SQLite database into the Firebase cloud store database. This has further inspired me to learn more about document databases for use in mobile and IoT. 

#### Artifact Files

[Artifact 3 Narrative](https://github.com/rjairam/rjairam.github.io/blob/main/Artifacts/Artifact%203%20narrative%20%20Ria%20Jairam.pdf)

[Artifact 3 Original](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%203%20Original)

[Artifact 3 Enhanced](https://github.com/rjairam/rjairam.github.io/tree/main/Artifacts/Artifact%203%20Enhanced)


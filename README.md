followt
=======

An app that tracks the followers of Twitter users and answers questions
such as "who unfollowed me, and when?"

This was written as an exercise in MongoDB to explore how the database
works and how to get the most out of it.  It is supposed to have
educational value, but is not really a polished, practical solution (yet).

How to run it
-------------

The app consists of a background process that scans Twitter followers
(Scanner.java) and a web UI that displays some basic follow/unfollow 
statistics about the accounts it monitors (WebUI.java).

* You need to have a Twitter API key and an access token, which you can
request at [dev.twitter.com](http://dev.twitter.com).  Put these into the file 
`src/main/resources/twitter.properties`.

* You need to have MongoDB installed and running on your local machine.

* Launch `net.followt.Scanner` as a background process.

* Launch `net.followt.WebUI`. It will run an embedded jetty server and
listen on port 4567 on the local machine.

* To start monitoring a user, insert a document into the `fscans` collection
in the `followt` database.  This document needs to contain the user's numeric
user id, e.g. `db.fscans.insert({"user_id" : 22603349})`.  The Scanner process
will perform one scan per minute (restricted to a batch of up to 5,000 users).
It will cycle through all users in the fscans collection periodically.

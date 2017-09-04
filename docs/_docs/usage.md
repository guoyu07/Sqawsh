---
title: Usage
layout: docs
---

## User types
There is a distinction between players - who can book and cancel single courts - and the admin user who administers the service and is allowed to make block bookings and also to set up booking rules for future or recurring bookings such as a weekly club night.
## Booking and cancelling courts
Players can book or cancel single courts at any time within a specified future time window (currently three weeks). The booking time slots are currently hard-coded at 45-minute intervals from 10AM.
![]({{ site.baseurl }}/img/ReservationView.png)
The admin user can also create block bookings across contiguous courts and times.
![]({{ site.baseurl }}/img/AdminReservationView.png)
## Creating booking rules
The admin user can create rules to book a court (or block of courts) at some future date. Optionally they can choose that the same booking is automatically made again at the same time on each following week. Particular weeks can optionally be excluded from such recurring bookings.
<img src="{{ site.baseurl }}/img/BookingRuleView.png" class="img40"/>
## Password flows
The admin user can change their password or start a forgotten-password flow from the login page.
<img src="{{ site.baseurl }}/img/LoginView.png" class="img40"/>
## Backup and restore
Every change to a booking or booking rule is backed up (as Json) as it's made to a versioned S3 bucket and to an SNS topic - to which the admin user's email is subscribed by Cloudformation when the service is created. Additionally, at midnight every day all bookings and booking rules on the system are backed up to the same destinations (S3 and SNS) as a single Json object. Bookings and booking rules can also be manually backed up to these same destinations at any time from the AWS console by invoking the `BackupBookingsAndBookingRulesLambda` Lambda function. All bookings and booking rules can be restored to the same (or a different) booking service using the same Json object pasted as input to the `RestoreBookingsAndBookingRulesLambda` Lambda function.
## Lifecycle State
To help with upgrading the service for bugfixes etc I've added a 'Lifecycle state' to the service. This can be 'Active', 'ReadOnly', or 'Retired'. The service can be changed between any of these states by running the `UpdateLifecycleStateLambda` Lambda function from the AWS console.
#### Active
This is the normal state of the system with all functionality available.
#### ReadOnly
In this state no bookings or booking rules can be made or changed but all existing bookings and rules can be viewed and a 'maintenance' banner is displayed on all pages. It is intended to allow a short time interval for a new (maybe bug-fixed) version of the service to be stood up and have all bookings and rules restored to it from the old service, which would then be transitioned to the Retired state.
#### Retired
In this state the service is unavailable even for viewing and redirects to a new version of the service. However it is otherwise preserved in tact and can, if required, be brought out of hibernation (e.g. if an updated version of the service needs to be rolled back).
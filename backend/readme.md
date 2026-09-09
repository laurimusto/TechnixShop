# Robust e-shop application for school project

jwt token (5min expiry) alongside with refresh token which has longer 

## Access token: 
in memory, in a JavaScript variable. It dies on page refresh, and the refresh token restores it.

## Refresh token: 
in an httpOnly, Secure, SameSite cookie. 
httpOnly means JavaScript cannot read it, 
so a cross-site scripting attack cannot steal it. Putting it in localStorage hands your long-lived credential to any injected script, 
which defeats the point of the whole design.
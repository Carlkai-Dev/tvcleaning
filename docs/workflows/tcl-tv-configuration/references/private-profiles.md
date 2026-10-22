# Reusing an existing private proxy profile

Use only for an owner's explicit request to reuse an already working local
profile. This is optional app configuration, not part of CleanYourTV's default
cleanup prompt and not a route around Netflix device compatibility.

An unavailable subscription endpoint does not establish that the cached profile
has stopped working. Identify the owner's selected profile locally; distinguish
its original cache from the generated effective configuration and preserve the
source. Do not print either file or its node/group contents into tool output.

For a TV app that supports local-file profiles:

1. Make a private working copy. Remove computer-specific controller/listener,
   interface, TUN and route bindings as appropriate for the target application's
   actual schema; do not rewrite node credentials or silently delete unsupported
   routing rules. Explain any material routing change to the owner.
2. Prefer a direct local import. If the app needs a temporary import URL, bind the
   transfer server to loopback, restrict it to that one file, use an unpredictable
   path and a short-lived authorized ADB reverse tunnel. Do not publish a LAN URL
   or treat the transfer endpoint as a subscription service.
3. If supported, clone/save the imported profile as a local **file** profile,
   activate it, then remove the temporary URL profile. Shut down the transfer
   server and remove only the reverse mapping created for this transfer.
4. Restart the target app after the transfer path has gone away. Verify that the
   selected profile is a local file and still works independently of the computer.
   A copied file does not gain automatic subscription updates.

Keep temporary files and local backups private with restrictive permissions.
Remove only session-created transfer files after verification. Never commit a
profile, subscription/transfer URL, node label, provider domain, UUID, controller
secret, key, account, private path, raw UI dump or connection log. A public result
can state only that a private local-file migration was confirmed; it needs no
sample profile. Owner-reported working configuration is not evidence that a
particular streaming service or region is unlocked.

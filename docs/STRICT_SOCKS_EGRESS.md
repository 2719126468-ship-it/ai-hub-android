# Strict SOCKS HTTPS egress check

## Purpose

`ProxyEgressTester.checkHttps` checks whether an HTTPS request can pass through
an already-running local SOCKS listener. It creates the URL connection with an
explicit `Proxy.Type.SOCKS` proxy at `127.0.0.1:<port>`.

The tester does not retry without a proxy, and it does not silently switch to a
direct connection. A successful check currently means the configured HTTPS
endpoint returned HTTP 204 through that local SOCKS connection.

## Current boundary

This helper is not yet wired to the node screen or a running sing-box
CommandServer. It cannot start the proxy itself. The next runtime change must
own CommandServer startup/shutdown and only call this tester after the local
listener is ready.

The Android URL connection stack's hostname-resolution behavior must also be
verified on supported Android versions. This helper does not claim remote DNS
resolution or DNS-leak protection.

## Result interpretation

- Success: the HTTPS endpoint returned the expected HTTP 204 through the
  explicit local SOCKS proxy.
- Failure: the listener was unavailable, the proxy handshake/remote request
  failed, TLS failed, or the endpoint returned an unexpected status.
- Latency: elapsed wall-clock time for the HTTPS request, including connection
  establishment and response headers. It is not a pure network RTT measurement.

A single successful request is not a stability score. Ranking should require
multiple timed samples and a separate failure-rate calculation.

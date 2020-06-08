#!/bin/bash

curl -x socks5h://127.0.0.1:1090 https://example.com
curl -x http://127.0.0.1:1090 http://example.com
curl -x http://127.0.0.1:1090 https://example.com

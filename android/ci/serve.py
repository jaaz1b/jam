# The test site's server for the saved-state test: like `python3 -m http.server`, but nothing may
# be kept by the browser's HTTP cache, so what shows offline comes from the site's worker or Jam.
import functools, http.server, os, sys

class NoStore(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

http.server.test(HandlerClass=functools.partial(NoStore, directory=os.path.dirname(os.path.abspath(__file__))),
                 port=int(sys.argv[1]) if len(sys.argv) > 1 else 8000, bind="127.0.0.1")

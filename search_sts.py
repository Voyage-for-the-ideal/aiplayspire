import urllib.request
import urllib.parse
import re
from html import unescape

def search(query):
    url = "https://lite.duckduckgo.com/lite/"
    data = urllib.parse.urlencode({'q': query}).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    })
    
    try:
        response = urllib.request.urlopen(req)
        html = response.read().decode('utf-8')
        
        # very simple extraction for duckduckgo lite
        results = re.findall(r'<a class="result-snippet[^>]+>(.*?)</a>', html, re.DOTALL)
        titles = re.findall(r'<a class="result-url[^>]+href="([^"]+)"[^>]*>(.*?)</a>', html, re.DOTALL)
        
        print(f"Results for '{query}':")
        for i, (link, title) in enumerate(titles):
            snippet = results[i].strip() if i < len(results) else ""
            print(f"{i+1}. {unescape(title)}\n   {link}\n   {unescape(snippet)}\n")
    except Exception as e:
        print(f"Error: {e}")

search("site:reddit.com/r/slaythespire Slay the Spire BaseMod console commands boss")
search("Slay the Spire console commands jump boss")

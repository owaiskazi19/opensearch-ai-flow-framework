import json
import jmespath

# Sample JSON data
json_data = '''
{
  "store": {
    "book": [
      {
        "title": "The Catcher in the Rye",
        "author": "J.D. Salinger",
        "price": 15.99
      },
      {
        "title": "To Kill a Mockingbird",
        "author": "Harper Lee",
        "price": 12.50
      }
    ],
    "bicycle": {
      "color": "red",
      "price": 199.99
    }
  }
}
'''

# Load JSON data
data = json.loads(json_data)

# Extract titles and prices of books using jmespath
jsonpath_expression = "store.book[*].{Title: title, Price: price}"
result = jmespath.search(jsonpath_expression, data)

filtered_books = [book for book in result if book["Price"] < 15]

#print(json.dumps(result, indent=2))

#print(filtered_books)

# Create OpenSearch Search Template 
for book in filtered_books:
    template_query = {
    "index_patterns": ["my-index"],
    "version": 1,
    "priority": 1,
    "template": {
        "source": {
            "query": {
                "bool": {
                    "must": [
                        {"match": {"title": book["Title"]}}
                    ],
                    "filter": [
                        {"range": {"price": {"lt": book["Price"]}}}
                    ]
                }
            }
        }
    }
  } 

print(json.dumps(template_query, indent=2))


###OUTPUT###
""" {
  "index_patterns": [
    "my-index"
  ],
  "version": 1,
  "priority": 1,
  "template": {
    "source": {
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "title": "To Kill a Mockingbird"
              }
            }
          ],
          "filter": [
            {
              "range": {
                "price": {
                  "lt": 12.5
                }
              }
            }
          ]
        }
      }
    }
  }
} """
# C4 Diagrams

Use these renderable Mermaid source files:

- `C4_CONTEXT.mmd` (System Context, C4 Level 1)
- `C4_CONTAINER.mmd` (Container View, C4 Level 2)

Example:

```bash
docker run --rm -u "$(id -u):$(id -g)" \
  -v "$PWD:/data" \
  minlag/mermaid-cli \
  -i /data/C4_CONTEXT.mmd -o /data/C4_CONTEXT.svg
```

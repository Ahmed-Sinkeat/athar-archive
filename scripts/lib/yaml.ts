export function parseYaml(content: string): any {
  const lines = content.split(/\r?\n/);
  const root: any = {};
  
  interface StackItem {
    indent: number;
    value: any;
    key?: string;
    type: "object" | "array";
  }
  
  const stack: StackItem[] = [{ indent: -1, value: root, type: "object" }];
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.trim().startsWith("#") || line.trim() === "") continue;
    
    const leadingSpaces = line.match(/^ */)?.[0].length || 0;
    const trimmed = line.trim();
    
    // Pop elements from stack that have larger or equal indentation
    while (stack.length > 0 && stack[stack.length - 1].indent >= leadingSpaces && stack[stack.length - 1].indent !== -1) {
      stack.pop();
    }
    
    const parent = stack[stack.length - 1];
    
    if (trimmed.startsWith("-")) {
      // It's a list item
      const afterDash = trimmed.slice(1).trim();
      
      // Ensure parent's key is initialized as array
      if (parent.type === "object" && parent.key) {
        const parentObj = stack[stack.length - 2].value;
        const key = parent.key;
        if (!Array.isArray(parentObj[key])) {
          parentObj[key] = [];
        }
        parent.value = parentObj[key];
        parent.type = "array";
      }
      
      // If the value is fully quoted, treat as a plain string (handles regex patterns with colons)
      const isFullyQuoted = (afterDash.startsWith('"') && afterDash.endsWith('"')) ||
                            (afterDash.startsWith("'") && afterDash.endsWith("'"));
      const colonIdx = afterDash.indexOf(":");
      if (!isFullyQuoted && colonIdx !== -1) {
        // It's an object inside a list, e.g. "- id: title"
        const key = afterDash.slice(0, colonIdx).trim();
        const valueStr = afterDash.slice(colonIdx + 1).trim().replace(/^['"]|['"]$/g, "");
        const newObj: any = {};
        newObj[key] = parsePrimitive(valueStr);
        parent.value.push(newObj);
        // Push the new object onto stack so subsequent indented lines add to this object
        stack.push({ indent: leadingSpaces, value: newObj, key, type: "object" });
      } else {
        // Plain value inside list — decode escape sequences if double-quoted
        const val = afterDash.startsWith('"') && afterDash.endsWith('"')
          ? unquoteYaml(afterDash.slice(1, -1))
          : afterDash.replace(/^'|'$/g, "");
        parent.value.push(parsePrimitive(val));
      }
    } else {
      // Key-value pair
      const colonIdx = trimmed.indexOf(":");
      if (colonIdx !== -1) {
        const key = trimmed.slice(0, colonIdx).trim();
        const rawVal = trimmed.slice(colonIdx + 1).trim();
        const valueStr = rawVal.startsWith('"') && rawVal.endsWith('"')
          ? unquoteYaml(rawVal.slice(1, -1))
          : rawVal.replace(/^'|'$/g, "");
        
        if (valueStr === "") {
          parent.value[key] = {};
          stack.push({ indent: leadingSpaces, value: parent.value[key], key, type: "object" });
        } else {
          parent.value[key] = parsePrimitive(valueStr);
        }
      }
    }
  }
  
  return root;
}

function parsePrimitive(str: string): any {
  if (str === "") return "";
  if (str === "true") return true;
  if (str === "false") return false;
  if (!isNaN(Number(str))) return Number(str);
  return str;
}

// Decode YAML double-quote escape sequences: \\ → \, \n → newline, etc.
function unquoteYaml(raw: string): string {
  return raw.replace(/\\([\\"ntrb])/g, (_, c) => {
    if (c === "n") return "\n";
    if (c === "t") return "\t";
    if (c === "r") return "\r";
    if (c === "b") return "\b";
    return c; // handles \\ → \ and \" → "
  });
}

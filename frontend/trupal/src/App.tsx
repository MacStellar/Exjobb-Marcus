import React, { useState } from "react";
import reactLogo from "./assets/react.svg";
import viteLogo from "/vite.svg";
import "./App.css";
import { Button } from "./components/ui/button";
import { ListGroup } from "@/components/ListGroup.tsx";
import { Textarea } from "@/components/ui/textarea";
import { Alertt } from "@/components/Alert2.tsx";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Terminal } from "lucide-react";

function App() {
  const [count, setCount] = useState(0);
  let items = ["Stockholm", "Jönköping", "Enköping"];
  const handleSelectItem = (value: string) => {
    console.log("nu triggas den i app", value);
  };

  return (
    <>
      <div>
        <a href="https://vitejs.dev" target="_blank">
          <img src={viteLogo} className="logo" alt="Vite logo" />
        </a>
        <a href="https://react.dev" target="_blank">
          <img src={reactLogo} className="logo react" alt="React logo" />
        </a>
      </div>
      <h1>Vite + React</h1>
      <div className="card">
        <button onClick={() => setCount((count) => count + 1)}>
          count is {count}
        </button>
        <Alertt>hej</Alertt>
        <Button> New type of button</Button>
        <ListGroup
          items={items}
          heading="List header"
          onSelectItem={handleSelectItem}
        />
        <Textarea />
        <Alert>
          <Terminal className="h-4 w-4" />
          <AlertTitle>Heads up!</AlertTitle>
          <AlertDescription>
            You can add components and dependencies to your app using the cli.
          </AlertDescription>
        </Alert>
        <br />

        <p>hej</p>
        <p>
          Edit <code>src/App.tsx</code> and save to test HMR
        </p>
      </div>
      <p className="read-the-docs">
        Click on the Vite and React logos to learn more
      </p>
    </>
  );
}

export default App;

import {ReactNode} from "react";

interface Props {
    children: ReactNode;
}

const Alertt = ({ children }: Props) => {
    return (
        <div>
            <p>
                { children}
            </p>
        </div>
    )
}

export { Alertt }
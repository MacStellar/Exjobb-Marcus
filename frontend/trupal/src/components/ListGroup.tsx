import { useState } from 'react'
import '../App.css'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"



interface ListGroupProps {
    items: string[];
    heading: string;
    onSelectItem: (value: string) => void;
}


function ListGroup({items, heading, onSelectItem}: ListGroupProps) {

    const [selectedIndex, setSelectedIndex] = useState(-1)

    return (
        <>
            <h1>{heading}</h1>
            <ul className="list-group">
                {items.map((item, index) => (
                    <li className={selectedIndex === index ? 'list-item' : 'list-item active:'} key={item} onClick={() => {
                        setSelectedIndex(index)
                        onSelectItem(item)
                    }}> {item}</li>
                ))}
            </ul>
            <Select onValueChange={(e) => {
                onSelectItem(e)
                setSelectedIndex(1)
            }}>
                <SelectTrigger className="w-[180px]">
                    <SelectValue placeholder="Theme" />
                </SelectTrigger>
                <SelectContent>
                    <SelectItem value="one" >One</SelectItem>
                    <SelectItem value="two">Two</SelectItem>
                    <SelectItem value="three">Three</SelectItem>
                </SelectContent>
            </Select>
        </>

    )
}

export {ListGroup}
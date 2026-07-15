import { createContext, useContext } from 'react';

export const MessagingContext = createContext();

export const useMessagingContext = () => useContext(MessagingContext);

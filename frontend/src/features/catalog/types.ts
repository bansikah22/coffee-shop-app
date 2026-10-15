export type Coffee = {
  id: number
  name: string
  description: string
  price: number
  available: boolean
  imageUrl?: string | null
}

export type CoffeeForm = Omit<Coffee, 'id'>

export const emptyCoffee: CoffeeForm = {
  name: '',
  description: '',
  price: 0,
  available: true,
}
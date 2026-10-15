import type { Coffee } from './types'
import americanoImage from '../../assets/americano-coffee.png'
import cappuccinoImage from '../../assets/Cappuccino-coffee.png'
import flatWhiteImage from '../../assets/FlatWhite-coffee.png'
import seasonalMochaImage from '../../assets/SeasonalMocha-coffee.png'

const coffeeImages: Record<string, string> = {
  Americano: americanoImage,
  Cappuccino: cappuccinoImage,
  'Flat White': flatWhiteImage,
  'Seasonal Mocha': seasonalMochaImage,
}

type CoffeeBoardProps = {
  coffees: Coffee[]
  onRefresh: () => void
  onOrder: (coffeeId: number) => void
}

export function CoffeeBoard({ coffees, onRefresh, onOrder }: CoffeeBoardProps) {
  return (
    <section className="coffee-board" id="coffee-board" aria-labelledby="board-title">
      <div className="section-heading">
        <div><p className="eyebrow">Today's selection</p><h2 id="board-title">The coffee board</h2></div>
        <button className="refresh-button" type="button" onClick={onRefresh}>Refresh</button>
      </div>
      <div className="coffee-grid">
        {coffees.map((coffee, index) => (
          <article className={`coffee-card card-tone-${index % 4}`} key={coffee.id}>
            {(coffee.imageUrl || coffeeImages[coffee.name]) && (
              <div className="coffee-card-media">
                <img src={coffee.imageUrl || coffeeImages[coffee.name]} alt={coffee.name} loading="lazy" />
              </div>
            )}
            <div className="coffee-card-top">
              <span className="coffee-number">0{index + 1}</span>
              <span className={coffee.available ? 'availability available' : 'availability'}>
                {coffee.available ? 'Available' : 'Paused'}
              </span>
            </div>
            <div className="coffee-copy"><h3>{coffee.name}</h3><p>{coffee.description}</p></div>
            <div className="coffee-card-bottom">
              <strong>${coffee.price.toFixed(2)}</strong>
              <button type="button" disabled={!coffee.available} onClick={() => onOrder(coffee.id)}>Order</button>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}